package com.tddsdd.service;

import com.tddsdd.dto.CreateOrderRequest;
import com.tddsdd.dto.OrderDTO;
import com.tddsdd.dto.OrderItemDTO;
import com.tddsdd.entity.Order;
import com.tddsdd.entity.OrderItem;
import com.tddsdd.entity.User;
import com.tddsdd.enums.OrderStatus;
import com.tddsdd.exception.InvalidOrderStateException;
import com.tddsdd.exception.ResourceNotFoundException;
import com.tddsdd.mapper.OrderMapper;
import com.tddsdd.repository.OrderRepository;
import com.tddsdd.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * HARD MODE test suite for OrderServiceImpl.
 * Tests inter-service dependencies, state machine transitions,
 * price calculations with discount tiers, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderServiceImpl;

    private User activeUser;
    private User inactiveUser;
    private Order sampleOrder;
    private OrderDTO sampleOrderDTO;
    private CreateOrderRequest createRequest;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(1L).name("John Doe").email("john@example.com").active(true).build();
        inactiveUser = User.builder()
                .id(2L).name("Inactive User").email("inactive@example.com").active(false).build();

        OrderItem item1 = OrderItem.builder()
                .id(1L).productName("Widget A").quantity(2)
                .unitPrice(new BigDecimal("25.00")).lineTotal(new BigDecimal("50.00")).build();
        OrderItem item2 = OrderItem.builder()
                .id(2L).productName("Widget B").quantity(1)
                .unitPrice(new BigDecimal("30.00")).lineTotal(new BigDecimal("30.00")).build();

        sampleOrder = Order.builder()
                .id(1L).user(activeUser).status(OrderStatus.PENDING)
                .items(List.of(item1, item2))
                .totalAmount(new BigDecimal("80.00"))
                .discountPercent(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("80.00"))
                .shippingAddress("123 Main St").build();

        sampleOrderDTO = OrderDTO.builder()
                .id(1L).userId(1L).userName("John Doe").status(OrderStatus.PENDING)
                .items(List.of(
                        OrderItemDTO.builder().id(1L).productName("Widget A").quantity(2)
                                .unitPrice(new BigDecimal("25.00")).lineTotal(new BigDecimal("50.00")).build(),
                        OrderItemDTO.builder().id(2L).productName("Widget B").quantity(1)
                                .unitPrice(new BigDecimal("30.00")).lineTotal(new BigDecimal("30.00")).build()
                ))
                .totalAmount(new BigDecimal("80.00"))
                .discountPercent(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("80.00"))
                .shippingAddress("123 Main St").build();

        createRequest = CreateOrderRequest.builder()
                .userId(1L)
                .shippingAddress("123 Main St")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productName("Widget A").quantity(2)
                                .unitPrice(new BigDecimal("25.00")).build(),
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productName("Widget B").quantity(1)
                                .unitPrice(new BigDecimal("30.00")).build()
                ))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CREATE ORDER
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("should create order for active user with correct calculations")
        void createOrder_success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
            when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);
            when(orderMapper.toDTO(any(Order.class))).thenReturn(sampleOrderDTO);
            when(orderMapper.toItemEntity(any(), any())).thenAnswer(invocation -> {
                CreateOrderRequest.OrderItemRequest req = invocation.getArgument(0);
                Order order = invocation.getArgument(1);
                return OrderItem.builder()
                        .productName(req.getProductName())
                        .quantity(req.getQuantity())
                        .unitPrice(req.getUnitPrice())
                        .lineTotal(req.getUnitPrice().multiply(BigDecimal.valueOf(req.getQuantity())))
                        .order(order)
                        .build();
            });

            OrderDTO result = orderServiceImpl.createOrder(createRequest);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);

            // Verify the order was saved with correct calculations
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();

            assertThat(savedOrder.getUser()).isEqualTo(activeUser);
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(savedOrder.getItems()).hasSize(2);
            // totalAmount = (2×25) + (1×30) = 80.00
            assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("80.00"));
            // No discount under 100
            assertThat(savedOrder.getDiscountPercent()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(savedOrder.getFinalAmount()).isEqualByComparingTo(new BigDecimal("80.00"));
        }

        @Test
        @DisplayName("should apply 10% discount for orders >= $100")
        void createOrder_10percentDiscount() {
            CreateOrderRequest bigOrder = CreateOrderRequest.builder()
                    .userId(1L).shippingAddress("456 Oak Ave")
                    .items(List.of(
                            CreateOrderRequest.OrderItemRequest.builder()
                                    .productName("Premium Widget").quantity(4)
                                    .unitPrice(new BigDecimal("30.00")).build()
                    )).build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toDTO(any(Order.class))).thenReturn(sampleOrderDTO);
            when(orderMapper.toItemEntity(any(), any())).thenAnswer(invocation -> {
                CreateOrderRequest.OrderItemRequest req = invocation.getArgument(0);
                Order order = invocation.getArgument(1);
                return OrderItem.builder()
                        .productName(req.getProductName())
                        .quantity(req.getQuantity())
                        .unitPrice(req.getUnitPrice())
                        .lineTotal(req.getUnitPrice().multiply(BigDecimal.valueOf(req.getQuantity())))
                        .order(order)
                        .build();
            });

            orderServiceImpl.createOrder(bigOrder);

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();

            // totalAmount = 4 × 30 = 120.00
            assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("120.00"));
            // 10% discount
            assertThat(savedOrder.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10"));
            // finalAmount = 120 - (120 × 10/100) = 108.00
            assertThat(savedOrder.getFinalAmount()).isEqualByComparingTo(new BigDecimal("108.00"));
        }

        @Test
        @DisplayName("should apply 20% discount for orders >= $500")
        void createOrder_20percentDiscount() {
            CreateOrderRequest hugeOrder = CreateOrderRequest.builder()
                    .userId(1L).shippingAddress("789 Elm Blvd")
                    .items(List.of(
                            CreateOrderRequest.OrderItemRequest.builder()
                                    .productName("Luxury Widget").quantity(10)
                                    .unitPrice(new BigDecimal("60.00")).build()
                    )).build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toDTO(any(Order.class))).thenReturn(sampleOrderDTO);
            when(orderMapper.toItemEntity(any(), any())).thenAnswer(invocation -> {
                CreateOrderRequest.OrderItemRequest req = invocation.getArgument(0);
                Order order = invocation.getArgument(1);
                return OrderItem.builder()
                        .productName(req.getProductName())
                        .quantity(req.getQuantity())
                        .unitPrice(req.getUnitPrice())
                        .lineTotal(req.getUnitPrice().multiply(BigDecimal.valueOf(req.getQuantity())))
                        .order(order)
                        .build();
            });

            orderServiceImpl.createOrder(hugeOrder);

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();

            // totalAmount = 10 × 60 = 600.00
            assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("600.00"));
            // 20% discount
            assertThat(savedOrder.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("20"));
            // finalAmount = 600 - (600 × 20/100) = 480.00
            assertThat(savedOrder.getFinalAmount()).isEqualByComparingTo(new BigDecimal("480.00"));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for non-existent user")
        void createOrder_userNotFound() {
            when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderServiceImpl.createOrder(createRequest))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw IllegalStateException for inactive user")
        void createOrder_inactiveUser() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(inactiveUser));

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(2L).shippingAddress("Addr")
                    .items(List.of(CreateOrderRequest.OrderItemRequest.builder()
                            .productName("X").quantity(1).unitPrice(BigDecimal.TEN).build()))
                    .build();

            assertThatThrownBy(() -> orderServiceImpl.createOrder(request))
                    .isInstanceOf(IllegalStateException.class);

            verify(orderRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET ORDERS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getOrders")
    class GetOrders {

        @Test
        @DisplayName("should get order by ID")
        void getOrderById_success() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));
            when(orderMapper.toDTO(sampleOrder)).thenReturn(sampleOrderDTO);

            OrderDTO result = orderServiceImpl.getOrderById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getItems()).hasSize(2);
            verify(orderRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for unknown order")
        void getOrderById_notFound() {
            when(orderRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderServiceImpl.getOrderById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should get orders by user ID")
        void getOrdersByUserId() {
            when(orderRepository.findByUserId(1L)).thenReturn(List.of(sampleOrder));
            when(orderMapper.toDTO(sampleOrder)).thenReturn(sampleOrderDTO);

            List<OrderDTO> result = orderServiceImpl.getOrdersByUserId(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should get orders by status")
        void getOrdersByStatus() {
            when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(sampleOrder));
            when(orderMapper.toDTO(sampleOrder)).thenReturn(sampleOrderDTO);

            List<OrderDTO> result = orderServiceImpl.getOrdersByStatus(OrderStatus.PENDING);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(OrderStatus.PENDING);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STATUS TRANSITIONS (State Machine)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateOrderStatus — State Machine")
    class UpdateOrderStatus {

        @Test
        @DisplayName("PENDING → CONFIRMED should succeed")
        void pendingToConfirmed() {
            Order pendingOrder = Order.builder()
                    .id(1L).user(activeUser).status(OrderStatus.PENDING).items(List.of())
                    .totalAmount(BigDecimal.TEN).discountPercent(BigDecimal.ZERO).finalAmount(BigDecimal.TEN).build();
            OrderDTO confirmedDTO = OrderDTO.builder()
                    .id(1L).userId(1L).userName("John Doe").status(OrderStatus.CONFIRMED).items(List.of()).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(pendingOrder);
            when(orderMapper.toDTO(any(Order.class))).thenReturn(confirmedDTO);

            OrderDTO result = orderServiceImpl.updateOrderStatus(1L, OrderStatus.CONFIRMED);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(orderRepository).save(pendingOrder);
        }

        @Test
        @DisplayName("CONFIRMED → SHIPPED should succeed")
        void confirmedToShipped() {
            Order confirmedOrder = Order.builder()
                    .id(1L).user(activeUser).status(OrderStatus.CONFIRMED).items(List.of())
                    .totalAmount(BigDecimal.TEN).discountPercent(BigDecimal.ZERO).finalAmount(BigDecimal.TEN).build();
            OrderDTO shippedDTO = OrderDTO.builder()
                    .id(1L).userId(1L).userName("John Doe").status(OrderStatus.SHIPPED).items(List.of()).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(confirmedOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(confirmedOrder);
            when(orderMapper.toDTO(any(Order.class))).thenReturn(shippedDTO);

            OrderDTO result = orderServiceImpl.updateOrderStatus(1L, OrderStatus.SHIPPED);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("SHIPPED → DELIVERED should succeed")
        void shippedToDelivered() {
            Order shippedOrder = Order.builder()
                    .id(1L).user(activeUser).status(OrderStatus.SHIPPED).items(List.of())
                    .totalAmount(BigDecimal.TEN).discountPercent(BigDecimal.ZERO).finalAmount(BigDecimal.TEN).build();
            OrderDTO deliveredDTO = OrderDTO.builder()
                    .id(1L).userId(1L).userName("John Doe").status(OrderStatus.DELIVERED).items(List.of()).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(shippedOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(shippedOrder);
            when(orderMapper.toDTO(any(Order.class))).thenReturn(deliveredDTO);

            OrderDTO result = orderServiceImpl.updateOrderStatus(1L, OrderStatus.DELIVERED);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
            assertThat(shippedOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("PENDING → SHIPPED should throw (skip not allowed)")
        void pendingToShipped_shouldThrow() {
            Order pendingOrder = Order.builder()
                    .id(1L).user(activeUser).status(OrderStatus.PENDING).items(List.of())
                    .totalAmount(BigDecimal.TEN).discountPercent(BigDecimal.ZERO).finalAmount(BigDecimal.TEN).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

            assertThatThrownBy(() -> orderServiceImpl.updateOrderStatus(1L, OrderStatus.SHIPPED))
                    .isInstanceOf(InvalidOrderStateException.class);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("DELIVERED → anything should throw (terminal state)")
        void deliveredToAnything_shouldThrow() {
            Order deliveredOrder = Order.builder()
                    .id(1L).user(activeUser).status(OrderStatus.DELIVERED).items(List.of())
                    .totalAmount(BigDecimal.TEN).discountPercent(BigDecimal.ZERO).finalAmount(BigDecimal.TEN).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(deliveredOrder));

            assertThatThrownBy(() -> orderServiceImpl.updateOrderStatus(1L, OrderStatus.CANCELLED))
                    .isInstanceOf(InvalidOrderStateException.class);

            verify(orderRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CANCEL ORDER
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("should cancel PENDING order")
        void cancelPendingOrder() {
            Order pendingOrder = Order.builder()
                    .id(1L).user(activeUser).status(OrderStatus.PENDING).items(List.of())
                    .totalAmount(BigDecimal.TEN).discountPercent(BigDecimal.ZERO).finalAmount(BigDecimal.TEN).build();
            OrderDTO cancelledDTO = OrderDTO.builder()
                    .id(1L).userId(1L).userName("John Doe").status(OrderStatus.CANCELLED).items(List.of()).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(pendingOrder);
            when(orderMapper.toDTO(any(Order.class))).thenReturn(cancelledDTO);

            OrderDTO result = orderServiceImpl.cancelOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderRepository).save(pendingOrder);
        }

        @Test
        @DisplayName("should cancel CONFIRMED order")
        void cancelConfirmedOrder() {
            Order confirmedOrder = Order.builder()
                    .id(1L).user(activeUser).status(OrderStatus.CONFIRMED).items(List.of())
                    .totalAmount(BigDecimal.TEN).discountPercent(BigDecimal.ZERO).finalAmount(BigDecimal.TEN).build();
            OrderDTO cancelledDTO = OrderDTO.builder()
                    .id(1L).userId(1L).userName("John Doe").status(OrderStatus.CANCELLED).items(List.of()).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(confirmedOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(confirmedOrder);
            when(orderMapper.toDTO(any(Order.class))).thenReturn(cancelledDTO);

            OrderDTO result = orderServiceImpl.cancelOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("should NOT cancel SHIPPED order")
        void cancelShippedOrder_shouldThrow() {
            Order shippedOrder = Order.builder()
                    .id(1L).user(activeUser).status(OrderStatus.SHIPPED).items(List.of())
                    .totalAmount(BigDecimal.TEN).discountPercent(BigDecimal.ZERO).finalAmount(BigDecimal.TEN).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(shippedOrder));

            assertThatThrownBy(() -> orderServiceImpl.cancelOrder(1L))
                    .isInstanceOf(InvalidOrderStateException.class);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("should NOT cancel DELIVERED order")
        void cancelDeliveredOrder_shouldThrow() {
            Order deliveredOrder = Order.builder()
                    .id(1L).user(activeUser).status(OrderStatus.DELIVERED).items(List.of())
                    .totalAmount(BigDecimal.TEN).discountPercent(BigDecimal.ZERO).finalAmount(BigDecimal.TEN).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(deliveredOrder));

            assertThatThrownBy(() -> orderServiceImpl.cancelOrder(1L))
                    .isInstanceOf(InvalidOrderStateException.class);

            verify(orderRepository, never()).save(any());
        }
    }
}
