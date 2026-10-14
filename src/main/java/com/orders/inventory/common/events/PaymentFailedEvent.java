package com.orders.inventory.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
    UUID eventId,

    UUID paymentId,

    UUID orderId,

    BigDecimal amount,

    String reason,

    Instant occurredAt 
) {}
