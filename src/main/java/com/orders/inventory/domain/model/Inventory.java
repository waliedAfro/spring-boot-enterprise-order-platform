package com.orders.inventory.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.orders.inventory.exception.InsufficientStockException ;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "inv_inventory",
        indexes = {
                @Index(name = "idx_inventory_product_id", columnList = "product_id"),
                @Index(name = "idx_inventory_created_at", columnList = "created_at")
        }
)
@Setter 
@Getter 
@AllArgsConstructor 
@NoArgsConstructor 
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Reference to the Product Service.
     *
     * We intentionally do NOT use:
     *
     * @ManyToOne
     * private Product product;
     *
     * because Product belongs to the Product module/service.
     */
    @Column(name = "product_id", nullable = false, unique = true)
    @NotNull
    private UUID productId;

    
    @Column(name = "quantity", nullable = false)
    @NotNull
    @Min(0)
    private Integer quantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    @NotNull
    @Min(0)
    private Integer reservedQuantity = 0;

    @Column(name = "available_quantity", nullable = false)
    @NotNull
    @Min(0)
    private Integer availableQuantity = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {

        Instant now = Instant.now() ;

        createdAt = now;
        updatedAt = now;

        if (quantity == null) {
            quantity = 0;
        }

        if (reservedQuantity == null) {
            reservedQuantity = 0;
        }

        calculateAvailableQuantity();
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt = Instant.now();

        calculateAvailableQuantity();
    }

    private void calculateAvailableQuantity() {

        if (quantity == null) {
            quantity = 0;
        }

        if (reservedQuantity == null) {
            reservedQuantity = 0;
        }

        availableQuantity = quantity - reservedQuantity;
    }

    /**
     * Reserves stock for an order.
     *
     * @param quantity quantity to reserve
     * @throws IllegalArgumentException if quantity is invalid
     * @throws InsufficientStockException if there is not enough stock
     */
    public void reserve(int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be greater than zero");
        }
 
        if (availableQuantity < quantity) {
            throw new InsufficientStockException(
                    productId,
                    availableQuantity,
                    quantity
                    
            );
        }

        availableQuantity -= quantity;
        reservedQuantity += quantity;
        updatedAt = Instant.now();
    }


}

