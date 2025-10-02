package ru.netology.currencyparser.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateRateRequest(
        @NotNull Long id,
        Double value
) {}
