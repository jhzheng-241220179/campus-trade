package com.campustrade.exception;

public class VerificationCodeLimitException
        extends IllegalStateException {

    public VerificationCodeLimitException(String message) {
        super(message);
    }
}