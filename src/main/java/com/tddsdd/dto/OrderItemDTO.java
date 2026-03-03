package com.tddsdd.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {
    private Long id;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
