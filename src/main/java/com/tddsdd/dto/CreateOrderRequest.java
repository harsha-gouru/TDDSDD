package com.tddsdd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotEmpty(message = "Order must have at least one item")
    private List<OrderItemRequest> items;

    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        @NotBlank(message = "Product name is required")
        private String productName;

        @Positive(message = "Quantity must be positive")
        private int quantity;

        @NotNull(message = "Unit price is required")
        @Positive(message = "Unit price must be positive")
        private BigDecimal unitPrice;
    }
}
