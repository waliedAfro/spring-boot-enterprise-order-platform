package com.orders.inventory.common.events;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventoryReservationFailedEvent {
    
    UUID eventId;
    String eventType;
    Instant occurredAt;
    UUID orderId;
    String reason;
    String orderNumber;
    UUID customerId;

}
