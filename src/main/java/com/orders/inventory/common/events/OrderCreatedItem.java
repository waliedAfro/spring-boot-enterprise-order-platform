package com.orders.inventory.common.events;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedItem(
        UUID productId,

        String productName,

        Integer quantity,

        BigDecimal unitPrice,

        BigDecimal subtotal) {}
