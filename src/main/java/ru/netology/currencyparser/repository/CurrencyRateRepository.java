package ru.netology.currencyparser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.netology.currencyparser.domain.CurrencyRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {

    Optional<CurrencyRate> findTopByCodeOrderByAsOfDateDesc(String code);

    List<CurrencyRate> findByAsOfDate(LocalDate date);

    boolean existsByCodeAndAsOfDate(String code, LocalDate asOfDate);

    Optional<CurrencyRate> findTopByCodeAndAsOfDateLessThanOrderByAsOfDateDesc(String code, LocalDate asOfDate);

    @Query("select max(c.asOfDate) from CurrencyRate c where c.asOfDate <= :date")
    LocalDate findMaxAsOfDateLe(@Param("date") LocalDate date);

    @Query("select r.code from CurrencyRate r where r.asOfDate = :asOf and r.code in :codes")
    Set<String> findCodesByAsOfDateAndCodeIn(@Param("asOf") LocalDate asOf,
                                             @Param("codes") Set<String> codes);

    @Query("""
            select r
            from CurrencyRate r
            where r.code in :codes and r.asOfDate < :asOf
            order by r.code asc, r.asOfDate desc
            """)
    List<CurrencyRate> findAllPrevForCodes(@Param("asOf") LocalDate asOf,
                                           @Param("codes") Set<String> codes);
}
