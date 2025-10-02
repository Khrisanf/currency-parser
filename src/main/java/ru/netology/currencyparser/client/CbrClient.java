package ru.netology.currencyparser.client;

import org.springframework.stereotype.Component;
import ru.netology.currencyparser.dto.RateDto;
import ru.netology.currencyparser.exception.NotImplementedYetException;

import java.util.List;

@Component
public class CbrClient implements ExternalRatesClient {

    // TODO: сюда придёт HTTP-клиент + XML десериализация
    @Override
    public List<RateDto> fetchLatest() {
        throw new NotImplementedYetException("CBR fetch: TODO (XML -> DTO)");
    }
}
