package com.orders.inventory.service.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.inventory.common.events.InventoryEventTypes;
import com.orders.inventory.common.events.InventoryReservationFailedEvent;
import com.orders.inventory.common.events.InventoryReservedEvent;
import com.orders.inventory.common.events.OrderCreatedEvent;
import com.orders.inventory.domain.model.Inventory;
import com.orders.inventory.exception.InventoryNotFoundException;
import com.orders.inventory.outbox.domain.model.OutboxEvent;
import com.orders.inventory.outbox.repository.OutboxEventRepository;
import com.orders.inventory.repository.InventoryRepository;
import com.orders.inventory.service.InventoryOrderReservationService;
import com.orders.inventory.exception.InsufficientStockException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryOrderReservationServiceImpl implements InventoryOrderReservationService {

    private final InventoryRepository inventoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void reserveOrderInventory(OrderCreatedEvent event) {

        log.info(
                "Processing inventory reservation: orderId={}, orderNumber={}",
                event.orderId(),
                event.orderNumber());

        try {

            /*
             * Reserve every item belonging to the order.
             *
             * The whole operation is inside one transaction.
             */
            for (var item : event.items()) {

                Inventory inventory = inventoryRepository
                        .findByProductIdForUpdate(item.productId())
                        .orElseThrow(() -> new InventoryNotFoundException(
                                "Inventory not found for productId="
                                        + item.productId()));

                if (inventory.getAvailableQuantity() < item.quantity()) {

                    createReservationFailedEvent(
                            event,
                            "Insufficient stock for productId="
                                    + item.productId());

                    log.warn(
                            "Inventory reservation failed: orderId={}, productId={}, requested={}, available={}",
                            event.orderId(),
                            item.productId(),
                            item.quantity(),
                            inventory.getAvailableQuantity());

                    return;
                }

                inventory.reserve(item.quantity());

                inventoryRepository.save(inventory);
            }

            createReservationSuccessEvent(event);

            log.info("Inventory successfully reserved: orderId={}", event.orderId());

        } catch (InsufficientStockException ex) {

            createReservationFailedEvent(
                    event,
                    ex.getMessage());

            return;
        }

        catch (Exception ex) {

            log.error("Unexpected inventory reservation error: orderId={}", event.orderId(), ex);

            createReservationFailedEvent(event, ex.getMessage() != null
                    ? ex.getMessage()
                    : "Inventory reservation failed");
        }
    }

    private void createReservationSuccessEvent(OrderCreatedEvent event) {

        InventoryReservedEvent reservedEvent = InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(InventoryEventTypes.INVENTORY_RESERVED)
                .occurredAt(Instant.now())
                .orderId(event.orderId())
                .build();

        saveOutboxEvent(reservedEvent.getEventId(), reservedEvent.getEventType(), reservedEvent.getOrderId(),
                reservedEvent);
    }

    private void createReservationFailedEvent(OrderCreatedEvent event, String reason) {

        InventoryReservationFailedEvent failedEvent = InventoryReservationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(InventoryEventTypes.INVENTORY_RESERVATION_FAILED)
                .occurredAt(Instant.now())
                .orderId(event.orderId())
                .reason(reason)
                .build();

        saveOutboxEvent(failedEvent.getEventId(), failedEvent.getEventType(), failedEvent.getOrderId(), failedEvent);
    }

    private void saveOutboxEvent(UUID eventId, String eventType, UUID aggregateId, Object payload) {

        try {

            OutboxEvent outboxEvent = new OutboxEvent();

            outboxEvent.setId(eventId);
            outboxEvent.setAggregateType("ORDER");
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setEventKey(aggregateId.toString());
            outboxEvent.setEventType(eventType);
            outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
            outboxEvent.setCreatedAt(Instant.now());
            outboxEvent.setRetryCount(0);
            outboxEvent.setNextRetryAt(Instant.now());

            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException ex) {

            throw new IllegalStateException("Failed to serialize inventory outbox event", ex);
        }
    }

}
