package ru.netology.currencyparser.dto;

import java.time.OffsetDateTime;

public record RateDto(
        Long id,
        String code,
        String name,
        String base,
        double value,
        Double change24h,
        String source,
        OffsetDateTime timestamp
) {}
