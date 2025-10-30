package ru.netology.currencyparser.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.netology.currencyparser.dto.CreateRateRequest;
import ru.netology.currencyparser.dto.RateDto;
import ru.netology.currencyparser.dto.UpdateRateRequest;
import ru.netology.currencyparser.exception.NotImplementedYetException;

import java.util.List;

@Service
@Transactional
public class RateServiceImpl implements RateService {

    @Override
    public RateDto create(CreateRateRequest req) {
        throw new NotImplementedYetException("Create rate: TODO");
    }

    @Override
    public RateDto update(UpdateRateRequest req) {
        throw new NotImplementedYetException("Update rate: TODO");
    }

    @Override
    public void delete(Long id) {
        throw new NotImplementedYetException("Delete rate: TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public RateDto getById(Long id) {
        throw new NotImplementedYetException("Get rate by id: TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public List<RateDto> getAll() {
        throw new NotImplementedYetException("List rates: TODO");
    }

    @Override
    public List<RateDto> fetchLatestFromSource() {
        throw new NotImplementedYetException("Fetch from CBR: TODO");
    }
}
