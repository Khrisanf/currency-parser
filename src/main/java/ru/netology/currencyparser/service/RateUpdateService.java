package ru.netology.currencyparser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.netology.currencyparser.client.CbrClient;
import ru.netology.currencyparser.domain.CurrencyRate;
import ru.netology.currencyparser.repository.CurrencyRateRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateUpdateService {

    private final CbrClient cbr;
    private final CurrencyRateRepository repo;

    // ручной однократный запуск
    public int updateOnce(LocalDate date) {
        var fetched = cbr.fetchDaily(date == null ? Optional.empty() : Optional.of(date));
        int saved = 0;
        for (var r : fetched) {
            var prevOpt = repo.findTopByCodeOrderByAsOfDateDesc(r.code());
            BigDecimal prev = prevOpt.map(CurrencyRate::getRateToRub).orElse(null);
            var entity = CurrencyRate.builder()
                    .code(r.code()).name(r.name()).nominal(r.nominal())
                    .rateToRub(r.rate()).asOfDate(r.date())
                    .prevRateToRub(prev)
                    .changeAbs(prev==null? null : r.rate().subtract(prev))
                    .changePct(prev==null? null : r.rate().divide(prev, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)))
                    .build();

            var existing = repo.findByCodeAndAsOfDate(r.code(), r.date());
            if (existing.isPresent()) {
                var e = existing.get();
                e.setName(entity.getName());
                e.setNominal(entity.getNominal());
                e.setRateToRub(entity.getRateToRub());
                e.setPrevRateToRub(entity.getPrevRateToRub());
                e.setChangeAbs(entity.getChangeAbs());
                e.setChangePct(entity.getChangePct());
                repo.save(e);
            } else {
                repo.save(entity);
            }
            saved++;
        }
        log.info("CBR update {}: saved {}", date, saved);
        return saved;
    }

    // авто-обновление
    @Scheduled(fixedDelayString = "${scheduler.update-ms}")
    public void scheduledUpdate() { updateOnce(null); }
}
