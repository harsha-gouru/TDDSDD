package com.tddsdd.service;

import com.tddsdd.dto.CreateProductRequest;
import com.tddsdd.dto.ProductDTO;
import com.tddsdd.dto.UpdateProductRequest;
import com.tddsdd.enums.ProductCategory;

import java.util.List;

/**
 * Product Management Service Contract.
 *
 * Business Rules:
 * - Product names must be unique (throws DuplicateResourceException)
 * - Price must be positive
 * - Stock quantity cannot be negative
 * - updateStock: adjusts stock by delta (positive = add, negative = remove)
 * - updateStock throws InsufficientStockException if result would be negative
 * - Deactivated products can still be retrieved by ID but not in active listings
 * - searchByName is case-insensitive
 */
public interface ProductService {

    ProductDTO createProduct(CreateProductRequest request);

    ProductDTO getProductById(Long id);

    List<ProductDTO> getAllActiveProducts();

    List<ProductDTO> getProductsByCategory(ProductCategory category);

    List<ProductDTO> searchByName(String name);

    ProductDTO updateProduct(Long id, UpdateProductRequest request);

    ProductDTO updateStock(Long id, int quantityDelta);

    void deactivateProduct(Long id);
}
