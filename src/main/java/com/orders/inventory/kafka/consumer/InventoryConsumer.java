package com.orders.inventory.kafka.consumer;

import java.time.Instant;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orders.inventory.common.events.OrderCreatedEvent;
import com.orders.inventory.idempotency.IdempotencyService;
import com.orders.inventory.outbox.service.impl.OutboxEventService;
import com.orders.inventory.service.InventoryService;

import com.orders.inventory.common.events.InventoryReservedEvent;
import com.orders.inventory.common.events.InventoryEventTypes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryConsumer {

    private static final String CONSUMER_NAME = "INVENTORY";

    private final IdempotencyService idempotencyService;
    private final InventoryService inventoryService;
    private final OutboxEventService outboxEventService;

    @KafkaListener(topics = "${spring.kafka.topics.order-created}", groupId = "${spring.kafka.consumer.group-id}",
     containerFactory = "orderCreatedKafkaListenerContainerFactory")
    public void consume(OrderCreatedEvent event) {

        log.info("Received OrderCreatedEvent: eventId={}, orderId={}", event.eventId(), event.orderId());

        // -------------------------------------------------
        // Idempotency check
        // -------------------------------------------------
        if (idempotencyService.alreadyProcessed(event.eventId(), CONSUMER_NAME)) {

            log.info("Duplicate inventory event ignored: eventId={}", event.eventId());

            return;
        }

        // -------------------------------------------------
        // Reserve inventory
        // -------------------------------------------------
        reserveInventory(event);

        InventoryReservedEvent reservedEvent = InventoryReservedEvent.builder()
                                                .eventId(UUID.randomUUID())
                                                .eventType(InventoryEventTypes.INVENTORY_RESERVED)
                                                .occurredAt(Instant.now())
                                                .orderId(event.orderId())
                                                .build() ;
        
        outboxEventService.saveInventoryReservedEvent(event.orderId(),reservedEvent);

        
        // -------------------------------------------------
        // Mark event as processed
        // -------------------------------------------------
        idempotencyService.markProcessed(event.eventId(), CONSUMER_NAME);
        log.info("Inventory processed successfully: orderId={}", event.orderId());

    }

    @Transactional 
    private void reserveInventory(OrderCreatedEvent event) {

        log.info(
                "Reserving inventory for orderId={}, items={}",
                event.orderId(),
                event.items().size()
        );

        event.items().forEach(item -> {

            log.info(
                    "Reserving inventory: orderId={}, productId={}, productName={}, quantity={}",
                    event.orderId(),
                    item.productId(),
                    item.productName(),
                    item.quantity()
            );

            inventoryService.reserveStock(item.productId(),item.quantity());
        });
    }
}
