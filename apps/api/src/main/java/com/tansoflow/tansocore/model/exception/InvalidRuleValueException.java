package com.tansoflow.tansocore.model.exception;

import lombok.Getter;

@Getter
public class InvalidRuleValueException extends RuntimeException {
    private final String detail;

    public InvalidRuleValueException(String message, String detail) {
        super(message);
        this.detail = detail;
    }
}
