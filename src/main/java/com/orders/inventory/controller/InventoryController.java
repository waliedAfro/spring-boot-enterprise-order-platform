package com.orders.inventory.controller;

import com.orders.inventory.domain.dto.InventoryRequest;
import com.orders.inventory.domain.dto.InventoryResponse;
import com.orders.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor 
public class InventoryController {

    private final InventoryService inventoryService;

    // =========================================================
    // CREATE INVENTORY
    // =========================================================

    @PostMapping
    public ResponseEntity<InventoryResponse> createInventory(
            @Valid @RequestBody InventoryRequest request) {

        InventoryResponse response = inventoryService.createInventory(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================
    // GET INVENTORY BY ID
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<InventoryResponse> getInventoryById(
            @PathVariable UUID id) {

        InventoryResponse response = inventoryService.getInventoryById(id);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // GET INVENTORY BY PRODUCT ID
    // =========================================================

    @GetMapping("/product/{productId}")
    public ResponseEntity<InventoryResponse> getInventoryByProductId(
            @PathVariable UUID productId) {

        InventoryResponse response = inventoryService.getInventoryByProductId(productId);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // UPDATE STOCK
    // =========================================================

    /**
     * Adjust stock quantity.
     *
     * Positive quantity:
     *     Add stock
     *
     * Negative quantity:
     *     Remove stock
     *
     * Example:
     *     quantity = 50
     *     current stock = 100
     *     new stock = 150
     */
    @PatchMapping("/product/{productId}/stock")
    public ResponseEntity<InventoryResponse> updateStock(
            @PathVariable UUID productId,
            @RequestParam int quantity) {

        InventoryResponse response =
                inventoryService.updateStock(productId,quantity);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // RESERVE STOCK
    // =========================================================

    @PostMapping("/product/{productId}/reserve")
    public ResponseEntity<InventoryResponse> reserveStock(
            @PathVariable UUID productId,
            @RequestParam int quantity) {

        InventoryResponse response =
                inventoryService.reserveStock(productId,quantity);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // RELEASE STOCK
    // =========================================================

    @PostMapping("/product/{productId}/release")
    public ResponseEntity<InventoryResponse> releaseStock(
            @PathVariable UUID productId,
            @RequestParam int quantity) {

        InventoryResponse response =
                inventoryService.releaseStock(productId,quantity);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // DEDUCT STOCK
    // =========================================================

    @PostMapping("/product/{productId}/deduct")
    public ResponseEntity<InventoryResponse> deductStock(
            @PathVariable UUID productId,
            @RequestParam int quantity) {

        InventoryResponse response =
                inventoryService.deductStock(productId,quantity);

        return ResponseEntity.ok(response);
    }
}

