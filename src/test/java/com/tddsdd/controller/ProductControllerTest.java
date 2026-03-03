package com.tddsdd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tddsdd.dto.CreateProductRequest;
import com.tddsdd.dto.ProductDTO;
import com.tddsdd.dto.UpdateProductRequest;
import com.tddsdd.enums.ProductCategory;
import com.tddsdd.exception.DuplicateResourceException;
import com.tddsdd.exception.InsufficientStockException;
import com.tddsdd.exception.ResourceNotFoundException;
import com.tddsdd.service.ProductService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    private ProductDTO sampleProductDTO;
    private CreateProductRequest createRequest;

    @BeforeEach
    void setUp() {
        sampleProductDTO = ProductDTO.builder()
                .id(1L).name("Laptop Pro").description("High-end laptop")
                .price(new BigDecimal("999.99")).stockQuantity(50)
                .category(ProductCategory.ELECTRONICS).active(true)
                .build();

        createRequest = CreateProductRequest.builder()
                .name("Laptop Pro").description("High-end laptop")
                .price(new BigDecimal("999.99")).stockQuantity(50)
                .category(ProductCategory.ELECTRONICS)
                .build();
    }

    @Test
    @DisplayName("POST /api/products → 201 Created")
    void createProduct_success() throws Exception {
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(sampleProductDTO);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Laptop Pro"))
                .andExpect(jsonPath("$.price").value(999.99))
                .andExpect(jsonPath("$.category").value("ELECTRONICS"));
    }

    @Test
    @DisplayName("POST /api/products → 409 Duplicate name")
    void createProduct_duplicate() throws Exception {
        when(productService.createProduct(any(CreateProductRequest.class)))
                .thenThrow(new DuplicateResourceException("Product with name 'Laptop Pro' already exists"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/products/{id} → 200 OK")
    void getProductById_success() throws Exception {
        when(productService.getProductById(1L)).thenReturn(sampleProductDTO);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Laptop Pro"));
    }

    @Test
    @DisplayName("GET /api/products → 200 OK (active products)")
    void getAllActiveProducts() throws Exception {
        when(productService.getAllActiveProducts()).thenReturn(List.of(sampleProductDTO));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    @DisplayName("GET /api/products/category/{category} → 200 OK")
    void getProductsByCategory() throws Exception {
        when(productService.getProductsByCategory(ProductCategory.ELECTRONICS))
                .thenReturn(List.of(sampleProductDTO));

        mockMvc.perform(get("/api/products/category/ELECTRONICS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("ELECTRONICS"));
    }

    @Test
    @DisplayName("GET /api/products/search?name=laptop → 200 OK")
    void searchByName() throws Exception {
        when(productService.searchByName("laptop")).thenReturn(List.of(sampleProductDTO));

        mockMvc.perform(get("/api/products/search").param("name", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Laptop Pro"));
    }

    @Test
    @DisplayName("PUT /api/products/{id}/stock?delta=10 → 200 OK")
    void updateStock_success() throws Exception {
        ProductDTO updated = ProductDTO.builder()
                .id(1L).name("Laptop Pro").stockQuantity(60)
                .category(ProductCategory.ELECTRONICS).active(true)
                .build();
        when(productService.updateStock(eq(1L), eq(10))).thenReturn(updated);

        mockMvc.perform(put("/api/products/1/stock").param("delta", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(60));
    }

    @Test
    @DisplayName("DELETE /api/products/{id} → 204 No Content")
    void deactivateProduct() throws Exception {
        doNothing().when(productService).deactivateProduct(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
    }
}
