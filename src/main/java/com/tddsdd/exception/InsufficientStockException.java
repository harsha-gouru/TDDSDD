package com.tddsdd.exception;

public class InsufficientStockException extends RuntimeException {
    
    private final String productName;
    private final int requested;
    private final int available;

    public InsufficientStockException(String productName, int requested, int available) {
        super(String.format("Insufficient stock for product '%s': requested %d, available %d",
                productName, requested, available));
        this.productName = productName;
        this.requested = requested;
        this.available = available;
    }

    public String getProductName() {
        return productName;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
