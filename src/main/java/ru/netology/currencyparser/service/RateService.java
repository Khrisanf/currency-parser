package ru.netology.currencyparser.service;

import ru.netology.currencyparser.dto.CreateRateRequest;
import ru.netology.currencyparser.dto.RateDto;
import ru.netology.currencyparser.dto.UpdateRateRequest;

import java.util.List;

public interface RateService {
    // CRUD
    RateDto create(CreateRateRequest req);
    RateDto update(UpdateRateRequest req);
    void delete(Long id);
    RateDto getById(Long id);
    List<RateDto> getAll();

    // Импорт с внешнего источника (ЦБ РФ)
    List<RateDto> fetchLatestFromSource();
}

