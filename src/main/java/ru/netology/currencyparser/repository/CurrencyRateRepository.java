package ru.netology.currencyparser.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.netology.currencyparser.domain.CurrencyRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {
    Optional<CurrencyRate> findTopByCodeOrderByAsOfDateDesc(String code);
    List<CurrencyRate> findByAsOfDate(LocalDate date);
    Optional<CurrencyRate> findByCodeAndAsOfDate(String code, LocalDate date);
}
