package ru.netology.currencyparser.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateUpdateService {

    private final CbrClient cbrClient;
    private final CurrencyRateRepository repo;
    private final PlatformTransactionManager txManager;

    @Qualifier("ioPool")
    private final Executor ioPool;

    private final MeterRegistry meterRegistry;

    @Value("${app.rates.batch-threads:3}")
    private int batchThreads;

    // ===== Метрики (этап 1) =====
    // время выполнения парсинга
    private Timer updateTimer;
    // таймер на батч
    private Timer batchTimer;

    // успехи /  ошибки запусков парсинга
    private Counter updateSuccess;
    private Counter updateError;

    // сколько записей попало в БД
    private Counter dbRowsInserted;

    // сколько ушло конфликтами
    private Counter dbConflicts;

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
        Timer.Sample totalSample = Timer.start(meterRegistry);

        try {
            List<CbrClient.CbrRate> list = cbrClient.fetchDaily(Optional.ofNullable(forcedDate));
            if (list == null || list.isEmpty()) {
                log.info("CBR: empty list for {}", forcedDate);
                updateSuccess.increment();
                return 0;
            }

            LocalDate asOf = list.get(0).date();

            int parts = Math.max(1, Math.min(batchThreads, list.size()));
            int chunkSize = (int) Math.ceil(list.size() / (double) parts);
            List<List<CbrClient.CbrRate>> batches = chunk(list, chunkSize);

            log.info("CBR: {} items for {}, {} batches (threads={})",
                    list.size(), asOf, batches.size(), parts);

            List<CompletableFuture<Integer>> futures = new ArrayList<>(batches.size());
            for (List<CbrClient.CbrRate> batch : batches) {
                List<CbrClient.CbrRate> safeCopy = new ArrayList<>(batch);
                futures.add(CompletableFuture.supplyAsync(
                        () -> runInNewTx(() -> processBatch(safeCopy, asOf)),
                        ioPool
                ));
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
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tt.execute((TransactionStatus status) -> task.get());
    }

    private int processBatch(List<CbrClient.CbrRate> batch, LocalDate asOf) {
        Timer.Sample batchSample = Timer.start(meterRegistry);

        log.info("batch start: size={}, date={}, thread={}", batch.size(), asOf, Thread.currentThread().getName());
        int saved = 0;

        try {
            for (CbrClient.CbrRate dto : batch) {
                final String code = dto.code();

                if (repo.existsByCodeAndAsOfDate(code, asOf)) {
                    continue;
                }

                var curr = dto.rate();
                var prevOpt = repo.findTopByCodeAndAsOfDateLessThanOrderByAsOfDateDesc(code, asOf);
                var prev = prevOpt.map(CurrencyRate::getRateToRub).orElse(null);

                var changeAbs = (prev == null) ? java.math.BigDecimal.ZERO : curr.subtract(prev);
                var changePct = (prev == null || prev.signum() == 0)
                        ? java.math.BigDecimal.ZERO
                        : changeAbs.divide(prev, 8, RoundingMode.HALF_UP)
                        .multiply(java.math.BigDecimal.valueOf(100));

                var entity = CurrencyRate.builder()
                        .code(code)
                        .name(dto.name())
                        .nominal(dto.nominal())
                        .rateToRub(curr)
                        .asOfDate(asOf)
                        .changeAbs(changeAbs)
                        .changePct(changePct)
                        .build();

                try {
                    repo.save(entity);
                    saved++;
                } catch (DataIntegrityViolationException ignore) {
                    dbConflicts.increment();
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
        updateOnce(null);
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