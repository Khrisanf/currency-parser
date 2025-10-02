package ru.netology.currencyparser.domain;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="currency_rates",
        uniqueConstraints=@UniqueConstraint(columnNames={"code","as_of_date"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CurrencyRate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=3) private String code;       // USD, EUR...
    @Column(nullable=false) private String name;                  // Наименование
    @Column(nullable=false) private int nominal;                  // Номинал
    @Column(nullable=false, precision=18, scale=6) private BigDecimal rateToRub; // Курс
    @Column(nullable=false) private LocalDate asOfDate;

    @Column(precision=18, scale=6) private BigDecimal prevRateToRub;
    @Column(precision=18, scale=6) private BigDecimal changeAbs;  // rate - prev
    @Column(precision=9,  scale=4) private BigDecimal changePct;  // (rate/prev - 1)*100
}
