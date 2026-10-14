package com.orders.inventory.outbox.service.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.orders.inventory.common.events.InventoryReservationFailedEvent;
import com.orders.inventory.common.events.InventoryEventTypes;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryFailureEventService {

    private final OutboxEventService outboxEventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishReservationFailed(UUID orderId, String reason) {

        InventoryReservationFailedEvent event = InventoryReservationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(InventoryEventTypes.INVENTORY_RESERVATION_FAILED)
                .occurredAt(Instant.now())
                .orderId(orderId)
                .reason(reason)
                .build();

        outboxEventService.saveInventoryReservationFailedEvent(orderId,event);
    }
}
