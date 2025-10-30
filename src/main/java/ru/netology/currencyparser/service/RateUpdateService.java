package ru.netology.currencyparser.service;

// package <твоя_пакетная_структура>;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.netology.currencyparser.client.CbrClient;
import ru.netology.currencyparser.domain.CurrencyRate;
import ru.netology.currencyparser.repository.CurrencyRateRepository;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    @Value("${app.rates.batch-threads:3}")
    private int batchThreads;

    public void updateOnce() {
        updateOnce(null);
    }

    public int updateOnce(LocalDate forcedDate) {
        List<CbrClient.CbrRate> list = cbrClient.fetchDaily(Optional.ofNullable(forcedDate));
        if (list == null || list.isEmpty()) {
            log.info("CBR: empty list for {}", forcedDate);
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
        log.info("CBR: update finished for {}, saved={}", asOf, totalSaved);
        return totalSaved;
    }

    private <T> T runInNewTx(Supplier<T> task) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tt.execute(status -> task.get());
    }

    private int processBatch(List<CbrClient.CbrRate> batch, LocalDate asOf) {
        int saved = 0;

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
            }
        }

        log.info("Batch persisted: saved={}, date={}", saved, asOf);
        return saved;
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
