package com.orders.inventory.service.impl;


import com.orders.inventory.domain.dto.InventoryRequest;
import com.orders.inventory.domain.dto.InventoryResponse;
import com.orders.inventory.domain.model.Inventory;
import com.orders.inventory.exception.DuplicateInventoryException;
import com.orders.inventory.exception.InventoryNotFoundException;
import com.orders.inventory.repository.InventoryRepository;
import com.orders.inventory.service.InventoryService;

import com.orders.inventory.exception.InsufficientStockException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;


    // =========================================================
    // CREATE
    // =========================================================

    @Override
    @Transactional
    public InventoryResponse createInventory(
            InventoryRequest request) {

        UUID productId = request.getProductId();

        if (inventoryRepository.existsByProductId(productId)) {
            throw new DuplicateInventoryException(productId);
        }

        Inventory inventory = new Inventory();

        inventory.setProductId(productId);
        inventory.setQuantity(request.getQuantity());
        inventory.setReservedQuantity(0);

        Inventory saved =
                inventoryRepository.save(inventory);

        return mapToResponse(saved);
    }


    // =========================================================
    // GET BY ID
    // =========================================================

    @Override
    public InventoryResponse getInventoryById(UUID id) {

        Inventory inventory = inventoryRepository
                .findById(id)
                .orElseThrow(
                        () -> new InventoryNotFoundException(id)
                );

        return mapToResponse(inventory);
    }


    // =========================================================
    // GET BY PRODUCT ID
    // =========================================================

    @Override
    public InventoryResponse getInventoryByProductId(
            UUID productId) {

        Inventory inventory = inventoryRepository
                .findByProductId(productId)
                .orElseThrow(
                        () -> InventoryNotFoundException
                                .byProductId(productId)
                );

        return mapToResponse(inventory);
    }


    // =========================================================
    // UPDATE STOCK
    // =========================================================

    @Override
    @Transactional
    public InventoryResponse updateStock(
            UUID productId,
            int quantity) {

        validateQuantity(quantity);

        Inventory inventory =
                getInventoryForUpdate(productId);

        int newQuantity =
                inventory.getQuantity() + quantity;

        if (newQuantity < inventory.getReservedQuantity()) {

            throw new IllegalStateException(
                    "Stock quantity cannot be less than "
                            + "reserved quantity"
            );
        }

        inventory.setQuantity(newQuantity);

        return mapToResponse(
                inventoryRepository.save(inventory)
        );
    }


    // =========================================================
    // RESERVE STOCK
    // =========================================================

    @Override
    @Transactional
    public InventoryResponse reserveStock(
            UUID productId,
            int quantity) {

        validateQuantity(quantity);

        /*
         * IMPORTANT:
         *
         * We must obtain the database lock BEFORE checking
         * availableQuantity.
         */
        Inventory inventory =
                getInventoryForUpdate(productId);

        int availableQuantity =
                inventory.getAvailableQuantity();

        if (availableQuantity < quantity) {

            throw new InsufficientStockException(productId , availableQuantity,quantity);
        }

        int newReservedQuantity =
                inventory.getReservedQuantity() + quantity;

        inventory.setReservedQuantity(
                newReservedQuantity
        );

        Inventory saved =
                inventoryRepository.save(inventory);

        logReservation(
                productId,
                quantity,
                saved
        );

        return mapToResponse(saved);
    }


    // =========================================================
    // RELEASE STOCK
    // =========================================================

    @Override
    @Transactional
    public InventoryResponse releaseStock(
            UUID productId,
            int quantity) {

        validateQuantity(quantity);

        Inventory inventory =
                getInventoryForUpdate(productId);

        int reservedQuantity =
                inventory.getReservedQuantity();

        if (reservedQuantity < quantity) {

            throw new IllegalStateException(
                    "Cannot release more stock than reserved "
                            + "for product: "
                            + productId
            );
        }

        inventory.setReservedQuantity(
                reservedQuantity - quantity
        );

        return mapToResponse(
                inventoryRepository.save(inventory)
        );
    }


    // =========================================================
    // DEDUCT STOCK
    // =========================================================

    @Override
    @Transactional
    public InventoryResponse deductStock(
            UUID productId,
            int quantity) {

        validateQuantity(quantity);

        Inventory inventory =
                getInventoryForUpdate(productId);

        int reservedQuantity =
                inventory.getReservedQuantity();

        if (reservedQuantity < quantity) {

            throw new IllegalStateException(
                    "Cannot deduct more stock than reserved "
                            + "for product: "
                            + productId
            );
        }

        inventory.setQuantity(
                inventory.getQuantity() - quantity
        );

        inventory.setReservedQuantity(
                reservedQuantity - quantity
        );

        return mapToResponse(
                inventoryRepository.save(inventory)
        );
    }


    // =========================================================
    // LOCKED INVENTORY
    // =========================================================

    private Inventory getInventoryForUpdate(
            UUID productId) {

        return inventoryRepository
                .findByProductIdForUpdate(productId)
                .orElseThrow(
                        () -> InventoryNotFoundException
                                .byProductId(productId)
                );
    }


    // =========================================================
    // VALIDATION
    // =========================================================

    private void validateQuantity(int quantity) {

        if (quantity <= 0) {

            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }
    }


    // =========================================================
    // LOGGING
    // =========================================================

    private void logReservation(
            UUID productId,
            int quantity,
            Inventory inventory) {

        // Keep logging centralized so reservation changes
        // can later be replaced by an InventoryTransaction
        // / audit mechanism.

        System.out.println(
                "Inventory reserved: "
                        + "productId=" + productId
                        + ", quantity=" + quantity
                        + ", available="
                        + inventory.getAvailableQuantity()
        );
    }


    // =========================================================
    // ENTITY → RESPONSE
    // =========================================================

    private InventoryResponse mapToResponse(
            Inventory inventory) {

        InventoryResponse response =
                new InventoryResponse();

        response.setId(inventory.getId());
        response.setProductId(inventory.getProductId());
        response.setQuantity(inventory.getQuantity());
        response.setReservedQuantity(
                inventory.getReservedQuantity()
        );
        response.setAvailableQuantity(
                inventory.getAvailableQuantity()
        );
        response.setCreatedAt(
                inventory.getCreatedAt()
        );
        response.setUpdatedAt(
                inventory.getUpdatedAt()
        );

        return response;
    }
}

