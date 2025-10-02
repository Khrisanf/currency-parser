package ru.netology.currencyparser.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
public class NotImplementedYetException extends RuntimeException {
    public NotImplementedYetException(String message) {
        super(message);
    }
}
