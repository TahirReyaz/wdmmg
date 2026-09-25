package com.wdmmg.expense.ai;

/** The model couldn't be reached or didn't return usable output. */
public class AiException extends RuntimeException {
    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
