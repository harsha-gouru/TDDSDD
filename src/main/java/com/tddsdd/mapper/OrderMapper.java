package com.tddsdd.mapper;

import com.tddsdd.dto.CreateOrderRequest;
import com.tddsdd.dto.OrderDTO;
import com.tddsdd.dto.OrderItemDTO;
import com.tddsdd.entity.Order;
import com.tddsdd.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OrderMapper — manual mapper (part of the contract).
 */
@Component
public class OrderMapper {

    public OrderDTO toDTO(Order order) {
        if (order == null) return null;

        List<OrderItemDTO> itemDTOs = order.getItems() != null
                ? order.getItems().stream().map(this::toItemDTO).collect(Collectors.toList())
                : List.of();

        return OrderDTO.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .userName(order.getUser().getName())
                .status(order.getStatus())
                .items(itemDTOs)
                .totalAmount(order.getTotalAmount())
                .discountPercent(order.getDiscountPercent())
                .finalAmount(order.getFinalAmount())
                .shippingAddress(order.getShippingAddress())
                .build();
    }

    public OrderItemDTO toItemDTO(OrderItem item) {
        if (item == null) return null;
        return OrderItemDTO.builder()
                .id(item.getId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .lineTotal(item.getLineTotal())
                .build();
    }

    public OrderItem toItemEntity(CreateOrderRequest.OrderItemRequest request, Order order) {
        if (request == null) return null;
        BigDecimal lineTotal = request.getUnitPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity()));
        return OrderItem.builder()
                .order(order)
                .productName(request.getProductName())
                .quantity(request.getQuantity())
                .unitPrice(request.getUnitPrice())
                .lineTotal(lineTotal)
                .build();
    }
}
