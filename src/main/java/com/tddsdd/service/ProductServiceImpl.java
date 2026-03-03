// FILE: src/main/java/com/tddsdd/service/ProductServiceImpl.java
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Override
    public ProductDTO createProduct(CreateProductRequest request) {
        if (productRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Product with name '" + request.getName() + "' already exists");
        }
        Product product = productMapper.toEntity(request);
        Product savedProduct = productRepository.save(product);
        return productMapper.toDTO(savedProduct);
    }

    @Override
    public ProductDTO getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return productMapper.toDTO(product);
    }

    @Override
    public List<ProductDTO> getAllActiveProducts() {
        return productRepository.findByActiveTrue().stream()
                .map(productMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductDTO> getProductsByCategory(ProductCategory category) {
        return productRepository.findByCategory(category).stream()
                .map(productMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductDTO> searchByName(String name) {
        return productRepository.findByNameContainingIgnoreCase(name).stream()
                .map(productMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ProductDTO updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        
        Product savedProduct = productRepository.save(product);
        return productMapper.toDTO(savedProduct);
    }

    @Override
    public ProductDTO updateStock(Long id, int quantityDelta) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        
        int newStock = product.getStockQuantity() + quantityDelta;
        if (newStock < 0) {
            throw new InsufficientStockException(product.getName(), Math.abs(quantityDelta), product.getStockQuantity());
        }
        
        product.setStockQuantity(newStock);
        Product savedProduct = productRepository.save(product);
        return productMapper.toDTO(savedProduct);
    }

    @Override
    public void deactivateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setActive(false);
        productRepository.save(product);
    }
}

