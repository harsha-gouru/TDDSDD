package com.tddsdd.enums;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /**
     * Valid transitions:
     * PENDING → CONFIRMED, CANCELLED
     * CONFIRMED → SHIPPED, CANCELLED
     * SHIPPED → DELIVERED
     * DELIVERED → (terminal)
     * CANCELLED → (terminal)
     */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
