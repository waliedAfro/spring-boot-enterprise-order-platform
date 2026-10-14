package com.orders.inventory.service;



import java.util.UUID;

import com.orders.inventory.domain.dto.InventoryRequest;
import com.orders.inventory.domain.dto.InventoryResponse;

public interface InventoryService {

    /**
     * Create inventory for a product.
     */
    InventoryResponse createInventory(InventoryRequest request);

    /**
     * Get inventory by inventory ID.
     */
    InventoryResponse getInventoryById(UUID id);

    /**
     * Get inventory by product ID.
     */
    InventoryResponse getInventoryByProductId(UUID productId);

    /**
     * Update the total stock quantity.
     *
     * The quantity can be increased or decreased, but it
     * cannot become lower than the currently reserved quantity.
     */
    InventoryResponse updateStock(UUID productId, int quantity);

    /**
     * Reserve available stock for an order.
     */
    InventoryResponse reserveStock(UUID productId, int quantity);

    /**
     * Release previously reserved stock.
     */
    InventoryResponse releaseStock(UUID productId, int quantity);

    /**
     * Permanently deduct stock.
     *
     * Typically called after the order has been successfully
     * processed and stock should no longer be available.
     */
    InventoryResponse deductStock(UUID productId, int quantity);
}
