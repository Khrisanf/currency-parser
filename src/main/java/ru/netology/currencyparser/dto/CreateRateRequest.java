package ru.netology.currencyparser.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRateRequest(
        @NotBlank String code,
        @NotBlank String base,
        @NotNull Double value
) {}
