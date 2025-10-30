package ru.netology.currencyparser.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
@Entity
@Table(name = "rates", indexes = {
        @Index(name = "ix_rates_code_base_ts", columnList = "code, base, timestamp")
})
public class Rate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 16, nullable = false)
    private String code;      // "USD", "EUR"

    @Column(length = 16, nullable = false)
    private String base;      // "RUB" или "USD"

    @Column(nullable = false)
    private double value;     // курс

    @Column
    private Double change24h; // изменение за 24ч, %

    @Column(length = 32)
    private String source;    // "CBR"

    @Column
    private OffsetDateTime timestamp;

    @Column(length = 128)
    private String name;      // имя валюты
}
