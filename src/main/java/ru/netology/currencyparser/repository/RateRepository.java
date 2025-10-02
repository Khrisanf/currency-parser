package ru.netology.currencyparser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.netology.currencyparser.model.Rate;

import java.util.Optional;

public interface RateRepository extends JpaRepository<Rate, Long> {
    Optional<Rate> findTop1ByCodeAndBaseOrderByTimestampDesc(String code, String base);
}
