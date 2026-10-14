package com.orders.inventory.exception;

import java.time.LocalDateTime;

public record ApiError(
        LocalDateTime timestamp,
        int status, String code,
        String message,
        String path) {}
