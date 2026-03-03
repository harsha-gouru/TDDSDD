package com.tddsdd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tddsdd.dto.CreateOrderRequest;
import com.tddsdd.dto.OrderDTO;
import com.tddsdd.dto.OrderItemDTO;
import com.tddsdd.enums.OrderStatus;
import com.tddsdd.exception.InvalidOrderStateException;
import com.tddsdd.exception.ResourceNotFoundException;
import com.tddsdd.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Level 3 HARD challenge — OrderController REST endpoint tests.
 * Tests: create, get by id, get by user, get by status, update status, cancel.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    private OrderDTO sampleOrderDTO;
    private CreateOrderRequest createRequest;

    @BeforeEach
    void setUp() {
        sampleOrderDTO = OrderDTO.builder()
                .id(1L).userId(1L).userName("John Doe")
                .status(OrderStatus.PENDING)
                .items(List.of(
                        OrderItemDTO.builder().id(1L).productName("Widget A")
                                .quantity(2).unitPrice(new BigDecimal("25.00"))
                                .lineTotal(new BigDecimal("50.00")).build()
                ))
                .totalAmount(new BigDecimal("50.00"))
                .discountPercent(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("50.00"))
                .shippingAddress("123 Main St")
                .build();

        createRequest = CreateOrderRequest.builder()
                .userId(1L).shippingAddress("123 Main St")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productName("Widget A").quantity(2)
                                .unitPrice(new BigDecimal("25.00")).build()
                ))
                .build();
    }

    @Test
    @DisplayName("POST /api/orders → 201 Created")
    void createOrder_success() throws Exception {
        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(sampleOrderDTO);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.shippingAddress").value("123 Main St"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} → 200 OK")
    void getOrderById_success() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(sampleOrderDTO);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].productName").value("Widget A"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} → 404 Not Found")
    void getOrderById_notFound() throws Exception {
        when(orderService.getOrderById(999L))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 999"));

        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/orders/user/{userId} → 200 OK")
    void getOrdersByUserId() throws Exception {
        when(orderService.getOrdersByUserId(1L)).thenReturn(List.of(sampleOrderDTO));

        mockMvc.perform(get("/api/orders/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].userId").value(1));
    }

    @Test
    @DisplayName("GET /api/orders/status/{status} → 200 OK")
    void getOrdersByStatus() throws Exception {
        when(orderService.getOrdersByStatus(OrderStatus.PENDING)).thenReturn(List.of(sampleOrderDTO));

        mockMvc.perform(get("/api/orders/status/PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("PUT /api/orders/{id}/status?status=CONFIRMED → 200 OK")
    void updateOrderStatus_success() throws Exception {
        OrderDTO confirmedDTO = OrderDTO.builder()
                .id(1L).userId(1L).userName("John Doe")
                .status(OrderStatus.CONFIRMED).items(List.of())
                .totalAmount(new BigDecimal("50.00"))
                .discountPercent(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("50.00")).build();

        when(orderService.updateOrderStatus(eq(1L), eq(OrderStatus.CONFIRMED))).thenReturn(confirmedDTO);

        mockMvc.perform(put("/api/orders/1/status")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("PUT /api/orders/{id}/cancel → 200 OK")
    void cancelOrder_success() throws Exception {
        OrderDTO cancelledDTO = OrderDTO.builder()
                .id(1L).userId(1L).userName("John Doe")
                .status(OrderStatus.CANCELLED).items(List.of())
                .totalAmount(new BigDecimal("50.00"))
                .discountPercent(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("50.00")).build();

        when(orderService.cancelOrder(1L)).thenReturn(cancelledDTO);

        mockMvc.perform(put("/api/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PUT /api/orders/{id}/cancel → 400 when shipped")
    void cancelOrder_shipped_throwsBadRequest() throws Exception {
        when(orderService.cancelOrder(1L))
                .thenThrow(new InvalidOrderStateException("SHIPPED", "CANCELLED"));

        mockMvc.perform(put("/api/orders/1/cancel"))
                .andExpect(status().isBadRequest());
    }
}
