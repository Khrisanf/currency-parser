package ru.netology.currencyparser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import ru.netology.currencyparser.client.CbrClient;
import ru.netology.currencyparser.domain.CurrencyRate;
import ru.netology.currencyparser.repository.CurrencyRateRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateUpdateServiceTest {

    @Mock
    CbrClient cbrClient;

    @Mock
    CurrencyRateRepository repo;

    @Mock
    PlatformTransactionManager txManager;

    RateUpdateService service;

    @BeforeEach
    void setUp() {
        Executor sameThread = Runnable::run;

        service = new RateUpdateService(cbrClient, repo, txManager, sameThread);

        ReflectionTestUtils.setField(service, "batchThreads", 1);
    }

    @Test
    void updateOnce_savesNewRate() {
        LocalDate date = LocalDate.of(2025, 1, 10);

        CbrClient.CbrRate dto = mock(CbrClient.CbrRate.class);
        when(dto.date()).thenReturn(date);
        when(dto.code()).thenReturn("USD");
        when(dto.name()).thenReturn("Доллар США");
        when(dto.nominal()).thenReturn(1);
        when(dto.rate()).thenReturn(new BigDecimal("90.1234"));

        // клиент вернул один курс
        when(cbrClient.fetchDaily(Optional.ofNullable(null)))
                .thenReturn(List.of(dto));

        // в БД такого курса ещё нет
        when(repo.existsByCodeAndAsOfDate("USD", date)).thenReturn(false);
        when(repo.findTopByCodeAndAsOfDateLessThanOrderByAsOfDateDesc("USD", date))
                .thenReturn(Optional.empty());

        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        int saved = service.updateOnce(null);

        assertEquals(1, saved);
        verify(repo, times(1)).save(any(CurrencyRate.class));
    }
}
