package com.tddsdd.dto;

import com.tddsdd.enums.OrderStatus;
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
public class OrderDTO {
    private Long id;
    private Long userId;
    private String userName;
    private OrderStatus status;
    private List<OrderItemDTO> items;
    private BigDecimal totalAmount;
    private BigDecimal discountPercent;
    private BigDecimal finalAmount;
    private String shippingAddress;
}
