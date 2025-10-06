package ru.netology.currencyparser.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.netology.currencyparser.domain.CurrencyRate;
import ru.netology.currencyparser.repository.CurrencyRateRepository;
import ru.netology.currencyparser.service.RateUpdateService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rates")
@RequiredArgsConstructor
public class RatesController {
    private final CurrencyRateRepository repo;
    private final RateUpdateService updater;

    @PostMapping("/update")
    public int update(@RequestParam(required = false) String date) {
        return updater.updateOnce(date==null? null : LocalDate.parse(date));
    }

    @GetMapping                      // все курсы за сегодня
    public List<CurrencyRate> today() {
        return repo.findByAsOfDate(LocalDate.now());
    }

    @GetMapping("/{code}")
    public CurrencyRate latest(@PathVariable String code) {
        return repo.findTopByCodeOrderByAsOfDateDesc(code.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No data for "+code));
    }

//    @PostMapping("/api/rates/import/latest-async")
//    public Map<String, Object> importAsync() {
//        rateService.startImportLatestAsync();
//        return Map.of("status", "started");
//    }
//
}
