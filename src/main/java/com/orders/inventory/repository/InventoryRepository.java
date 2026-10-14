package com.orders.inventory.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orders.inventory.domain.model.Inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    /**
     * Find inventory by product ID.
     */
    Optional<Inventory> findByProductId(UUID productId);

    /**
     * Check whether inventory exists for a product.
     */
    boolean existsByProductId(UUID productId);

    /**
     * * Find inventory by product ID and acquire a database
     * pessimistic write lock.
     * The row remains locked until the current transaction
     * commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM Inventory i WHERE i.productId = :productId
            """)
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") UUID productId);

    /**
     * Find inventory records where stock is available.
     */
    List<Inventory> findByAvailableQuantityGreaterThan(Integer quantity);

    /**
     * Find inventory records where stock is below or equal
     * to the specified quantity.
     */
    List<Inventory> findByAvailableQuantityLessThanEqual(Integer quantity);

    /**
     * Find inventory records where reserved stock exists.
     */
    List<Inventory> findByReservedQuantityGreaterThan(Integer quantity);
}
