package ru.netology.currencyparser.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.netology.currencyparser.dto.CreateRateRequest;
import ru.netology.currencyparser.dto.RateDto;
import ru.netology.currencyparser.dto.UpdateRateRequest;
import ru.netology.currencyparser.service.RateService;

import java.util.List;

@RestController
@RequestMapping("/api/rates")
public class RatesController {

    private final RateService rateService;

    public RatesController(RateService rateService) {
        this.rateService = rateService;
    }

    // CRUD
    @PostMapping
    public ResponseEntity<RateDto> create(@RequestBody @Valid CreateRateRequest req) {
        return ResponseEntity.ok(rateService.create(req));
    }

    @PutMapping
    public ResponseEntity<RateDto> update(@RequestBody @Valid UpdateRateRequest req) {
        return ResponseEntity.ok(rateService.update(req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        rateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RateDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(rateService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<RateDto>> list() {
        return ResponseEntity.ok(rateService.getAll());
    }

    // Импорт из внешнего источника (ЦБ)
    @PostMapping("/import/cbr")
    public ResponseEntity<List<RateDto>> importFromCbr() {
        return ResponseEntity.ok(rateService.fetchLatestFromSource());
    }
}
