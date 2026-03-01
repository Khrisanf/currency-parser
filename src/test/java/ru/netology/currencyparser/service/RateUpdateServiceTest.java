package ru.netology.currencyparser.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.Set;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        var meterRegistry = new SimpleMeterRegistry();
        var observationRegistry = ObservationRegistry.create();

        service = new RateUpdateService(
                cbrClient,
                repo,
                txManager,
                sameThread,
                meterRegistry,
                observationRegistry
        );

        ReflectionTestUtils.invokeMethod(service, "initMetrics");
        ReflectionTestUtils.setField(service, "batchThreads", 1);

        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doNothing().when(txManager).commit(any());
        doNothing().when(txManager).rollback(any());
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

        when(cbrClient.fetchDaily(eq(Optional.empty())))
                .thenReturn(List.of(dto));

        // в БД на эту дату кода USD ещё нет
        when(repo.findCodesByAsOfDateAndCodeIn(eq(date), argThat(s -> s != null && s.contains("USD"))))
                .thenReturn(Set.of());

        when(repo.findAllPrevForCodes(eq(date), argThat(s -> s != null && s.contains("USD"))))
                .thenReturn(List.of());

        int saved = service.updateOnce(null);

        assertEquals(1, saved);

        ArgumentCaptor<List<CurrencyRate>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo, times(1)).saveAll(captor.capture());

        List<CurrencyRate> batch = captor.getValue();
        assertNotNull(batch);
        assertEquals(1, batch.size());
        assertEquals("USD", batch.get(0).getCode());
        assertEquals(date, batch.get(0).getAsOfDate());
    }
}