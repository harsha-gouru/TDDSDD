package com.tddsdd.exception;

public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }

    public InvalidOrderStateException(String currentStatus, String targetStatus) {
        super(String.format("Cannot transition from %s to %s", currentStatus, targetStatus));
    }
}
