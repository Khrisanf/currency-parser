package ru.netology.currencyparser.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.netology.currencyparser.client.CbrClient;
import ru.netology.currencyparser.domain.CurrencyRate;
import ru.netology.currencyparser.repository.CurrencyRateRepository;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Service
@Slf4j
public class RateUpdateService {

    private final CbrClient cbrClient;
    private final CurrencyRateRepository repo;
    private final PlatformTransactionManager txManager;
    private final Executor ioPool;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    @Value("${app.rates.batch-threads:3}")
    private int batchThreads;

    // ===== Метрики =====
    private Timer updateTimer;
    private Timer batchTimer;

    private Counter updateSuccess;
    private Counter updateError;

    private Counter dbRowsInserted;
    private Counter dbConflicts;

    public RateUpdateService(CbrClient cbrClient,
                             CurrencyRateRepository repo,
                             PlatformTransactionManager txManager,
                             @Qualifier("ioPool") Executor ioPool,
                             MeterRegistry meterRegistry,
                             ObservationRegistry observationRegistry) {
        this.cbrClient = cbrClient;
        this.repo = repo;
        this.txManager = txManager;
        this.ioPool = ioPool;
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    @PostConstruct
    void initMetrics() {
        this.updateTimer = Timer.builder("cbr.update.duration")
                .description("Total duration of one CBR updateOnce run")
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.batchTimer = Timer.builder("cbr.update.batch.duration")
                .description("Duration of one batch processing")
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.updateSuccess = Counter.builder("cbr.update.success")
                .description("Successful updateOnce executions")
                .register(meterRegistry);

        this.updateError = Counter.builder("cbr.update.error")
                .description("Failed updateOnce executions")
                .register(meterRegistry);

        this.dbRowsInserted = Counter.builder("cbr.db.rows_inserted")
                .description("Inserted rows into currency_rate table")
                .register(meterRegistry);

        this.dbConflicts = Counter.builder("cbr.db.conflicts")
                .description("DataIntegrityViolation conflicts during save")
                .register(meterRegistry);
    }

    public int updateOnce(LocalDate forcedDate) {
        return Observation.createNotStarted("cbr.updateOnce", observationRegistry)
                .lowCardinalityKeyValue("forcedDate", String.valueOf(forcedDate))
                .observe(() -> doUpdateOnce(forcedDate));
    }

    private int doUpdateOnce(LocalDate forcedDate) {
        Timer.Sample totalSample = Timer.start(meterRegistry);

        try {
            List<CbrClient.CbrRate> list = Observation.createNotStarted("cbr.fetchDaily", observationRegistry)
                    .observe(() -> cbrClient.fetchDaily(Optional.ofNullable(forcedDate)));

            if (list == null || list.isEmpty()) {
                log.info("CBR: empty list for {}", forcedDate);
                updateSuccess.increment();
                return 0;
            }

            LocalDate asOf = list.get(0).date();

            List<List<CbrClient.CbrRate>> batches = Observation.createNotStarted("cbr.splitBatches", observationRegistry)
                    .lowCardinalityKeyValue("items", String.valueOf(list.size()))
                    .observe(() -> {
                        int parts = Math.max(1, Math.min(batchThreads, list.size()));
                        int chunkSize = (int) Math.ceil(list.size() / (double) parts);
                        return chunk(list, chunkSize);
                    });

            log.info("CBR: {} items for {}, {} batches (threads={})",
                    list.size(), asOf, batches.size(), Math.min(batchThreads, list.size()));

            // ✅ фикс: создаём snapshot, который будем протаскивать в потоки пула
            ContextSnapshot snapshot = ContextSnapshot.captureAll();

            List<CompletableFuture<Integer>> futures = new ArrayList<>(batches.size());

            for (List<CbrClient.CbrRate> batch : batches) {
                List<CbrClient.CbrRate> safeCopy = new ArrayList<>(batch);

                // ✅ фикс: явно делаем Callable<Integer>, тогда у результата есть .call()
                Callable<Integer> task = snapshot.wrap(() ->
                        runInNewTx(() -> processBatch(safeCopy, asOf))
                );

                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, ioPool));
            }

            int totalSaved = futures.stream().mapToInt(CompletableFuture::join).sum();

            updateSuccess.increment();
            log.info("CBR: update finished for {}, saved={}", asOf, totalSaved);
            return totalSaved;

        } catch (Exception e) {
            updateError.increment();
            throw e;
        } finally {
            totalSample.stop(updateTimer);
        }
    }

    private <T> T runInNewTx(Supplier<T> task) {
        return Observation.createNotStarted("tx.requires_new", observationRegistry)
                .observe(() -> {
                    TransactionTemplate tt = new TransactionTemplate(txManager);
                    tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    return tt.execute((TransactionStatus status) -> task.get());
                });
    }

    private int processBatch(List<CbrClient.CbrRate> batch, LocalDate asOf) {
        return Observation.createNotStarted("cbr.processBatch", observationRegistry)
                .lowCardinalityKeyValue("batchSize", String.valueOf(batch.size()))
                .lowCardinalityKeyValue("asOf", String.valueOf(asOf))
                .observe(() -> doProcessBatch(batch, asOf));
    }

    private int doProcessBatch(List<CbrClient.CbrRate> batch, LocalDate asOf) {
        Timer.Sample batchSample = Timer.start(meterRegistry);

        log.info("batch start: size={}, date={}, thread={}", batch.size(), asOf, Thread.currentThread().getName());

        try {
            if (batch.isEmpty()) {
                log.info("batch done: saved=0, thread={}", Thread.currentThread().getName());
                return 0;
            }

            Set<String> codes = Observation.createNotStarted("cbr.batch.collectCodes", observationRegistry)
                    .observe(() -> {
                        Set<String> out = new HashSet<>(batch.size());
                        for (CbrClient.CbrRate dto : batch) out.add(dto.code());
                        return out;
                    });

            Set<String> existingCodes = Observation.createNotStarted("db.read.existingCodes", observationRegistry)
                    .lowCardinalityKeyValue("codes", String.valueOf(codes.size()))
                    .observe(() -> repo.findCodesByAsOfDateAndCodeIn(asOf, codes));

            List<CurrencyRate> prevList = Observation.createNotStarted("db.read.prevRates", observationRegistry)
                    .lowCardinalityKeyValue("codes", String.valueOf(codes.size()))
                    .observe(() -> repo.findAllPrevForCodes(asOf, codes));

            Map<String, java.math.BigDecimal> prevByCode = new HashMap<>();
            for (CurrencyRate r : prevList) {
                prevByCode.putIfAbsent(r.getCode(), r.getRateToRub());
            }

            List<CurrencyRate> toSave = Observation.createNotStarted("cbr.batch.buildEntities", observationRegistry)
                    .observe(() -> {
                        List<CurrencyRate> out = new ArrayList<>(batch.size());

                        for (CbrClient.CbrRate dto : batch) {
                            final String code = dto.code();
                            if (existingCodes.contains(code)) continue;

                            var curr = dto.rate();
                            var prev = prevByCode.get(code);

                            var changeAbs = (prev == null) ? java.math.BigDecimal.ZERO : curr.subtract(prev);
                            var changePct = (prev == null || prev.signum() == 0)
                                    ? java.math.BigDecimal.ZERO
                                    : changeAbs.divide(prev, 8, RoundingMode.HALF_UP)
                                    .multiply(java.math.BigDecimal.valueOf(100));

                            out.add(CurrencyRate.builder()
                                    .code(code)
                                    .name(dto.name())
                                    .nominal(dto.nominal())
                                    .rateToRub(curr)
                                    .asOfDate(asOf)
                                    .changeAbs(changeAbs)
                                    .changePct(changePct)
                                    .build());
                        }
                        return out;
                    });

            int saved = 0;

            if (!toSave.isEmpty()) {
                try {
                    Observation.createNotStarted("db.write.saveAll", observationRegistry)
                            .lowCardinalityKeyValue("rows", String.valueOf(toSave.size()))
                            .observe(() -> {
                                repo.saveAll(toSave);
                                return null;
                            });

                    saved = toSave.size();
                } catch (DataIntegrityViolationException ignore) {
                    dbConflicts.increment();
                    saved = 0;
                }
            }

            if (saved > 0) {
                dbRowsInserted.increment(saved);
            }

            log.info("Batch persisted: saved={}, date={}", saved, asOf);
            log.info("batch done: saved={}, thread={}", saved, Thread.currentThread().getName());
            return saved;

        } finally {
            batchSample.stop(batchTimer);
        }
    }

    @Scheduled(fixedDelayString = "${scheduler.update-ms}")
    public void scheduledUpdate() {
        Observation.createNotStarted("scheduler.scheduledUpdate", observationRegistry)
                .observe(() -> {
                    updateOnce(null);
                    return null;
                });
    }

    @Transactional(readOnly = true)
    public LocalDate resolveEffectiveDate(LocalDate requested) {
        LocalDate req = (requested != null) ? requested : LocalDate.now();

        LocalDate effective = repo.findMaxAsOfDateLe(req);
        if (effective != null) return effective;
        return fetchAndResolve(req);
    }

    @Transactional
    protected LocalDate fetchAndResolve(LocalDate req) {
        updateOnce(req);

        LocalDate eff = repo.findMaxAsOfDateLe(req);
        if (eff != null) return eff;
        updateOnce(null);
        return repo.findMaxAsOfDateLe(req);
    }

    @Transactional(readOnly = true)
    public List<CurrencyRate> getRatesEffectiveOn(LocalDate requested) {
        LocalDate eff = resolveEffectiveDate(requested);
        return (eff == null) ? List.of() : repo.findByAsOfDate(eff);
    }

    private static <T> List<List<T>> chunk(List<T> src, int chunkSize) {
        if (src.isEmpty()) return List.of();
        if (chunkSize <= 0) return List.of(List.copyOf(src));
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < src.size(); i += chunkSize) {
            int to = Math.min(i + chunkSize, src.size());
            out.add(src.subList(i, to));
        }
        return out;
    }
}