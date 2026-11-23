package com.flagship.server.exception;

import java.util.List;

public class RuleValidationException extends RuntimeException {

    private final List<String> errors;

    public RuleValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors != null ? List.copyOf(errors) : List.of();
    }

    public List<String> getErrors() {
        return errors;
    }
}
