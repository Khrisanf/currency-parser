package ru.netology.currencyparser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.netology.currencyparser.domain.CurrencyRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {

    Optional<CurrencyRate> findTopByCodeOrderByAsOfDateDesc(String code);

    List<CurrencyRate> findByAsOfDate(LocalDate date);

    boolean existsByCodeAndAsOfDate(String code, LocalDate asOfDate);

    Optional<CurrencyRate> findTopByCodeAndAsOfDateLessThanOrderByAsOfDateDesc(String code, LocalDate asOfDate);

    @Query("select max(c.asOfDate) from CurrencyRate c where c.asOfDate <= :date")
    LocalDate findMaxAsOfDateLe(@Param("date") LocalDate date);
}
