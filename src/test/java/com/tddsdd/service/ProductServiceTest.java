package com.tddsdd.service;

import com.tddsdd.dto.CreateProductRequest;
import com.tddsdd.dto.ProductDTO;
import com.tddsdd.dto.UpdateProductRequest;
import com.tddsdd.entity.Product;
import com.tddsdd.enums.ProductCategory;
import com.tddsdd.exception.DuplicateResourceException;
import com.tddsdd.exception.InsufficientStockException;
import com.tddsdd.exception.ResourceNotFoundException;
import com.tddsdd.mapper.ProductMapper;
import com.tddsdd.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productServiceImpl;

    private Product sampleProduct;
    private ProductDTO sampleProductDTO;
    private CreateProductRequest createRequest;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id(1L).name("Laptop Pro").description("High-end laptop")
                .price(new BigDecimal("999.99")).stockQuantity(50)
                .category(ProductCategory.ELECTRONICS).active(true)
                .build();

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

    @Nested
    @DisplayName("Create Product")
    class CreateProduct {

        @Test
        @DisplayName("Should create product successfully")
        void createProduct_success() {
            when(productRepository.existsByName("Laptop Pro")).thenReturn(false);
            when(productMapper.toEntity(createRequest)).thenReturn(sampleProduct);
            when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);
            when(productMapper.toDTO(sampleProduct)).thenReturn(sampleProductDTO);

            ProductDTO result = productServiceImpl.createProduct(createRequest);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Laptop Pro");
            assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("999.99"));
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException when name exists")
        void createProduct_duplicateName() {
            when(productRepository.existsByName("Laptop Pro")).thenReturn(true);

            assertThatThrownBy(() -> productServiceImpl.createProduct(createRequest))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Products")
    class GetProducts {

        @Test
        @DisplayName("Should get product by ID")
        void getProductById_success() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            when(productMapper.toDTO(sampleProduct)).thenReturn(sampleProductDTO);

            ProductDTO result = productServiceImpl.getProductById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when not found")
        void getProductById_notFound() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productServiceImpl.getProductById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should get all active products")
        void getAllActiveProducts() {
            when(productRepository.findByActiveTrue()).thenReturn(List.of(sampleProduct));
            when(productMapper.toDTO(sampleProduct)).thenReturn(sampleProductDTO);

            List<ProductDTO> result = productServiceImpl.getAllActiveProducts();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isActive()).isTrue();
        }

        @Test
        @DisplayName("Should get products by category")
        void getProductsByCategory() {
            when(productRepository.findByCategory(ProductCategory.ELECTRONICS))
                    .thenReturn(List.of(sampleProduct));
            when(productMapper.toDTO(sampleProduct)).thenReturn(sampleProductDTO);

            List<ProductDTO> result = productServiceImpl.getProductsByCategory(ProductCategory.ELECTRONICS);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategory()).isEqualTo(ProductCategory.ELECTRONICS);
        }

        @Test
        @DisplayName("Should search products by name (case-insensitive)")
        void searchByName() {
            when(productRepository.findByNameContainingIgnoreCase("laptop"))
                    .thenReturn(List.of(sampleProduct));
            when(productMapper.toDTO(sampleProduct)).thenReturn(sampleProductDTO);

            List<ProductDTO> result = productServiceImpl.searchByName("laptop");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).containsIgnoringCase("laptop");
        }
    }

    @Nested
    @DisplayName("Update Product")
    class UpdateProduct {

        @Test
        @DisplayName("Should update product successfully")
        void updateProduct_success() {
            UpdateProductRequest updateReq = UpdateProductRequest.builder()
                    .name("Laptop Pro Max").description("Updated description")
                    .price(new BigDecimal("1299.99")).stockQuantity(100)
                    .build();
            Product updated = Product.builder()
                    .id(1L).name("Laptop Pro Max").description("Updated description")
                    .price(new BigDecimal("1299.99")).stockQuantity(100)
                    .category(ProductCategory.ELECTRONICS).active(true)
                    .build();
            ProductDTO updatedDTO = ProductDTO.builder()
                    .id(1L).name("Laptop Pro Max").description("Updated description")
                    .price(new BigDecimal("1299.99")).stockQuantity(100)
                    .category(ProductCategory.ELECTRONICS).active(true)
                    .build();

            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            when(productRepository.save(any(Product.class))).thenReturn(updated);
            when(productMapper.toDTO(updated)).thenReturn(updatedDTO);

            ProductDTO result = productServiceImpl.updateProduct(1L, updateReq);

            assertThat(result.getName()).isEqualTo("Laptop Pro Max");
            assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("1299.99"));
        }
    }

    @Nested
    @DisplayName("Stock Management")
    class StockManagement {

        @Test
        @DisplayName("Should increase stock")
        void updateStock_increase() {
            Product afterIncrease = Product.builder()
                    .id(1L).name("Laptop Pro").price(new BigDecimal("999.99"))
                    .stockQuantity(60).category(ProductCategory.ELECTRONICS).active(true)
                    .build();
            ProductDTO afterDTO = ProductDTO.builder()
                    .id(1L).name("Laptop Pro").stockQuantity(60)
                    .category(ProductCategory.ELECTRONICS).active(true)
                    .build();

            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            when(productRepository.save(any(Product.class))).thenReturn(afterIncrease);
            when(productMapper.toDTO(afterIncrease)).thenReturn(afterDTO);

            ProductDTO result = productServiceImpl.updateStock(1L, 10);

            assertThat(result.getStockQuantity()).isEqualTo(60);
        }

        @Test
        @DisplayName("Should decrease stock")
        void updateStock_decrease() {
            Product afterDecrease = Product.builder()
                    .id(1L).name("Laptop Pro").price(new BigDecimal("999.99"))
                    .stockQuantity(45).category(ProductCategory.ELECTRONICS).active(true)
                    .build();
            ProductDTO afterDTO = ProductDTO.builder()
                    .id(1L).name("Laptop Pro").stockQuantity(45)
                    .category(ProductCategory.ELECTRONICS).active(true)
                    .build();

            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            when(productRepository.save(any(Product.class))).thenReturn(afterDecrease);
            when(productMapper.toDTO(afterDecrease)).thenReturn(afterDTO);

            ProductDTO result = productServiceImpl.updateStock(1L, -5);

            assertThat(result.getStockQuantity()).isEqualTo(45);
        }

        @Test
        @DisplayName("Should throw InsufficientStockException when not enough stock")
        void updateStock_insufficient() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

            assertThatThrownBy(() -> productServiceImpl.updateStock(1L, -100))
                    .isInstanceOf(InsufficientStockException.class);
            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Deactivate Product")
    class DeactivateProduct {

        @Test
        @DisplayName("Should deactivate product")
        void deactivateProduct_success() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
            when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

            productServiceImpl.deactivateProduct(1L);

            verify(productRepository).save(argThat(p -> !p.isActive()));
        }
    }
}
