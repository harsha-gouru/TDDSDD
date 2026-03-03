package com.tddsdd.mapper;

import com.tddsdd.dto.CreateProductRequest;
import com.tddsdd.dto.ProductDTO;
import com.tddsdd.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductDTO toDTO(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .category(product.getCategory())
                .active(product.isActive())
                .build();
    }

    public Product toEntity(CreateProductRequest request) {
        return Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .category(request.getCategory())
                .active(true)
                .build();
    }
}
