package com.tddsdd.controller;

import com.tddsdd.dto.CreateProductRequest;
import com.tddsdd.dto.ProductDTO;
import com.tddsdd.dto.UpdateProductRequest;
import com.tddsdd.enums.ProductCategory;
import com.tddsdd.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductDTO> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductDTO product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable Long id) {
        ProductDTO product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAllActiveProducts() {
        List<ProductDTO> products = productService.getAllActiveProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProductDTO>> getProductsByCategory(@PathVariable ProductCategory category) {
        List<ProductDTO> products = productService.getProductsByCategory(category);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductDTO>> searchByName(@RequestParam String name) {
        List<ProductDTO> products = productService.searchByName(name);
        return ResponseEntity.ok(products);
    }

    @PutMapping("/{id}/stock")
    public ResponseEntity<ProductDTO> updateStock(@PathVariable Long id, @RequestParam int delta) {
        ProductDTO product = productService.updateStock(id, delta);
        return ResponseEntity.ok(product);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateProduct(@PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }
}

