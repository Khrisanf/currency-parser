package ru.netology.currencyparser.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.netology.currencyparser.domain.CurrencyRate;
import ru.netology.currencyparser.repository.CurrencyRateRepository;
import ru.netology.currencyparser.service.RateUpdateService;

import java.time.LocalDate;
import java.util.List;

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

    @GetMapping // "актуально на сегодня"
    public List<CurrencyRate> list() {
        return updater.getRatesEffectiveOn(null);
    }

    @GetMapping(params = "date")
    public List<CurrencyRate> listOn(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return updater.getRatesEffectiveOn(date);
    }


    @GetMapping("/{code}")
    public CurrencyRate latest(@PathVariable String code) {
        return repo.findTopByCodeOrderByAsOfDateDesc(code.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No data for "+code));
    }
}
