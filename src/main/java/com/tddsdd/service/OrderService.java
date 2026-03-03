package com.tddsdd.service;

import com.tddsdd.dto.CreateOrderRequest;
import com.tddsdd.dto.OrderDTO;
import com.tddsdd.enums.OrderStatus;

import java.util.List;

public interface OrderService {

    OrderDTO createOrder(CreateOrderRequest request);

    OrderDTO getOrderById(Long id);

    List<OrderDTO> getOrdersByUserId(Long userId);

    List<OrderDTO> getOrdersByStatus(OrderStatus status);

    OrderDTO updateOrderStatus(Long id, OrderStatus newStatus);

    OrderDTO cancelOrder(Long id);
}

