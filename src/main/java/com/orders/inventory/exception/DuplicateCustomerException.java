package com.orders.inventory.exception;

public class DuplicateCustomerException extends RuntimeException {

    public DuplicateCustomerException(String field, String value) {
        super("Customer already exists with " + field + ": " + value);
    }

    public static DuplicateCustomerException byCustomerNumber(
            String customerNumber) {

        return new DuplicateCustomerException(
                "customer number",
                customerNumber
        );
    }

    public static DuplicateCustomerException byEmail(String email) {

        return new DuplicateCustomerException(
                "email",
                email
        );
    }
}

