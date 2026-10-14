package com.orders.inventory.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.orders.inventory.domain.model.PaymentMethod;

public record OrderCreatedEvent(
        UUID eventId,
        
        String eventType,

        Instant occurredAt,

        UUID orderId,

        String orderNumber,

        UUID customerId,

        BigDecimal totalAmount,

        String currency ,
        
        PaymentMethod paymentMethod ,
        
        List<OrderCreatedItem> items) {}
