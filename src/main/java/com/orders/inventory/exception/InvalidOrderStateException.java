package com.orders.inventory.exception;

public class InvalidOrderStateException  extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
