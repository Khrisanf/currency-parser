package ru.netology.currencyparser.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import ru.netology.currencyparser.domain.CurrencyRate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CurrencyRateRepositoryTest {

    @Autowired
    CurrencyRateRepository repo;

    @Test
    void findTopByCodeOrderByAsOfDateDesc_returnsLatest() {
        var d1 = LocalDate.of(2025, 1, 10);
        var d2 = LocalDate.of(2025, 1, 11);

        repo.save(CurrencyRate.builder()
                .code("USD")
                .name("Доллар США")
                .nominal(1)
                .rateToRub(new BigDecimal("90.00"))
                .asOfDate(d1)
                .build());

        repo.save(CurrencyRate.builder()
                .code("USD")
                .name("Доллар США")
                .nominal(1)
                .rateToRub(new BigDecimal("91.00"))
                .asOfDate(d2)
                .build());

        var latest = repo.findTopByCodeOrderByAsOfDateDesc("USD");

        assertThat(latest).isPresent();
        assertThat(latest.get().getAsOfDate()).isEqualTo(d2);
    }

    @Test
    void findMaxAsOfDateLe_returnsMaxDateNotAfter() {
        repo.save(CurrencyRate.builder()
                .code("EUR")
                .name("Евро")
                .nominal(1)
                .rateToRub(new BigDecimal("100.00"))
                .asOfDate(LocalDate.of(2025, 1, 5))
                .build());

        repo.save(CurrencyRate.builder()
                .code("EUR")
                .name("Евро")
                .nominal(1)
                .rateToRub(new BigDecimal("101.00"))
                .asOfDate(LocalDate.of(2025, 1, 8))
                .build());

        var max = repo.findMaxAsOfDateLe(LocalDate.of(2025, 1, 9));

        assertThat(max).isEqualTo(LocalDate.of(2025, 1, 8));
    }

    @Test
    void existsByCodeAndAsOfDate_returnsTrueForExactMatch() {
        var date = LocalDate.of(2025, 1, 10);

        repo.save(CurrencyRate.builder()
                .code("GBP")
                .name("Фунт")
                .nominal(1)
                .rateToRub(new BigDecimal("115.00"))
                .asOfDate(date)
                .build());

        assertThat(repo.existsByCodeAndAsOfDate("GBP", date)).isTrue();
        assertThat(repo.existsByCodeAndAsOfDate("GBP", date.plusDays(1))).isFalse();
    }
}
