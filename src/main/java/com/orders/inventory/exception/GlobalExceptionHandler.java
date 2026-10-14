package com.orders.inventory.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                ex.getMessage(), request.getRequestURI());
    }

    
    
    @ExceptionHandler(DuplicateCustomerException.class)
    public ResponseEntity<ApiError> handleDuplicateCustomer(DuplicateCustomerException ex,
            HttpServletRequest request) {

        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_CUSTOMER",
                ex.getMessage(), request.getRequestURI());

    }

    @ExceptionHandler(DuplicateInventoryException.class)
    public ResponseEntity<ApiError> handleDuplicateCustomer(DuplicateInventoryException ex,
            HttpServletRequest request) {

        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_PRODUCT_IN_INVENTORY",
                ex.getMessage(), request.getRequestURI());

    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex,
            HttpServletRequest request) {

        return buildResponse(HttpStatus.NOT_FOUND, "INSUFFICIENT_STOCK_IN_INVENTORY",
                ex.getMessage(), request.getRequestURI());

    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                message, request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)

    public ResponseEntity<ApiError> handleGeneralException(Exception ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR", "An unexpected error occurred",
                request.getRequestURI());
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status,
            String code, String message, String path) {
        ApiError error = new ApiError(LocalDateTime.now(), status.value(),
                code, message, path);
        return ResponseEntity.status(status).body(error);
    }

}
