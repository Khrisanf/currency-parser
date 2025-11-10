package ru.netology.currencyparser.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.netology.currencyparser.repository.CurrencyRateRepository;
import ru.netology.currencyparser.service.RateUpdateService;
import ru.netology.currencyparser.domain.CurrencyRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RatesController.class)
class RatesControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RateUpdateService rateUpdateService;

    @MockitoBean
    CurrencyRateRepository currencyRateRepository;

    @Test
    void list_returns200() throws Exception {
        Mockito.when(rateUpdateService.getRatesEffectiveOn(isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/rates"))
                .andExpect(status().isOk());
    }

    @Test
    void listOnDate_returns200() throws Exception {
        LocalDate date = LocalDate.of(2025, 1, 10);

        Mockito.when(rateUpdateService.getRatesEffectiveOn(eq(date)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/rates")
                        .param("date", "2025-01-10"))
                .andExpect(status().isOk());
    }

    @Test
    void latest_returns200_whenFound() throws Exception {
        CurrencyRate mockRate = Mockito.mock(CurrencyRate.class);
        Mockito.when(currencyRateRepository
                        .findTopByCodeOrderByAsOfDateDesc("USD"))
                .thenReturn(Optional.of(mockRate));

        mockMvc.perform(get("/api/rates/USD"))
                .andExpect(status().isOk());
    }
}
