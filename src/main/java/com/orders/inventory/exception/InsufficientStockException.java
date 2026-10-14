package com.orders.inventory.exception;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(
            UUID productId,
            int available,
            int requested) {

        super(
            "Insufficient stock for product " + productId
                + ". Available: " + available
                + ", Requested: " + requested
        );
    }

}
