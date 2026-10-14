package com.orders.inventory.exception;

import java.util.UUID;

public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(UUID inventoryId) {
        super("Inventory not found with id: " + inventoryId);
    }

    public InventoryNotFoundException(String message) {
        super(message);
    }

    public static InventoryNotFoundException byProductId(UUID productId) {
        return new InventoryNotFoundException(
                "Inventory not found for product id: " + productId
        );
    }
}


