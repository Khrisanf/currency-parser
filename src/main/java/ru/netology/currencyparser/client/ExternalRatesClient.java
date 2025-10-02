package ru.netology.currencyparser.client;

import ru.netology.currencyparser.dto.RateDto;

import java.util.List;

public interface ExternalRatesClient {
    List<RateDto> fetchLatest();
}
