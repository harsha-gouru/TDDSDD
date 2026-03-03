package com.tddsdd.repository;

import com.tddsdd.entity.Product;
import com.tddsdd.enums.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategory(ProductCategory category);

    List<Product> findByActiveTrue();

    List<Product> findByNameContainingIgnoreCase(String name);

    Optional<Product> findByName(String name);

    boolean existsByName(String name);
}
