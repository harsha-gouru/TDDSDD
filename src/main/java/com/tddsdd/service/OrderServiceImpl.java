package com.tddsdd.service;

import com.tddsdd.dto.CreateOrderRequest;
import com.tddsdd.dto.OrderDTO;
import com.tddsdd.entity.Order;
import com.tddsdd.entity.OrderItem;
import com.tddsdd.entity.User;
import com.tddsdd.enums.OrderStatus;
import com.tddsdd.exception.InvalidOrderStateException;
import com.tddsdd.exception.ResourceNotFoundException;
import com.tddsdd.mapper.OrderMapper;
import com.tddsdd.repository.OrderRepository;
import com.tddsdd.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderRepository orderRepository, UserRepository userRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderMapper = orderMapper;
    }

    @Override
    public OrderDTO createOrder(CreateOrderRequest request) {
        // Validate user exists
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));

        // Validate user is active
        if (!user.isActive()) {
            throw new IllegalStateException("User must be active to create an order");
        }

        // Create Order entity
        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .shippingAddress(request.getShippingAddress())
                .build();

        // Create OrderItem entities and calculate totalAmount
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = request.getItems().stream()
                .map(itemRequest -> {
                    OrderItem item = orderMapper.toItemEntity(itemRequest, order);
                    return item;
                })
                .collect(Collectors.toList());

        for (OrderItem item : items) {
            totalAmount = totalAmount.add(item.getLineTotal());
        }

        order.setItems(items);
        order.setTotalAmount(totalAmount);

        // Calculate discount and final amount based on tier
        BigDecimal discountPercent = calculateDiscount(totalAmount);
        order.setDiscountPercent(discountPercent);

        BigDecimal finalAmount = calculateFinalAmount(totalAmount, discountPercent);
        order.setFinalAmount(finalAmount);

        // Save and return DTO
        Order savedOrder = orderRepository.save(order);
        return orderMapper.toDTO(savedOrder);
    }

    @Override
    public OrderDTO getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return orderMapper.toDTO(order);
    }

    @Override
    public List<OrderDTO> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(orderMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderDTO> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status).stream()
                .map(orderMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public OrderDTO updateOrderStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        // Validate state transition
        if (!order.getStatus().canTransitionTo(newStatus)) {
            throw new InvalidOrderStateException(order.getStatus().toString(), newStatus.toString());
        }

        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);
        return orderMapper.toDTO(savedOrder);
    }

    @Override
    public OrderDTO cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        // Can only cancel PENDING or CONFIRMED orders
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(order.getStatus().toString(), OrderStatus.CANCELLED.toString());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        return orderMapper.toDTO(savedOrder);
    }

    private BigDecimal calculateDiscount(BigDecimal totalAmount) {
        if (totalAmount.compareTo(new BigDecimal("500")) >= 0) {
            return new BigDecimal("20");
        } else if (totalAmount.compareTo(new BigDecimal("100")) >= 0) {
            return new BigDecimal("10");
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateFinalAmount(BigDecimal totalAmount, BigDecimal discountPercent) {
        if (discountPercent.compareTo(BigDecimal.ZERO) == 0) {
            return totalAmount;
        }
        BigDecimal discountAmount = totalAmount.multiply(discountPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return totalAmount.subtract(discountAmount);
    }
}

