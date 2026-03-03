# Product Management Feature Specification

## Overview
Product catalog with inventory/stock management.

## API Endpoints

| Method | Endpoint | Status | Description |
|--------|----------|--------|-------------|
| POST | /api/products | 201 | Create product |
| GET | /api/products/{id} | 200 | Get product by ID |
| GET | /api/products | 200 | Get all active products |
| GET | /api/products/category/{category} | 200 | Filter by category |
| GET | /api/products/search?name=X | 200 | Search by name (case-insensitive) |
| PUT | /api/products/{id} | 200 | Update product details |
| PUT | /api/products/{id}/stock?delta=N | 200 | Adjust stock (positive=add, negative=remove) |
| DELETE | /api/products/{id} | 204 | Deactivate product (soft delete) |

## Business Rules

### Product Creation
- Product names must be **unique** → 409 DuplicateResourceException
- Price must be positive
- Category is required (ELECTRONICS, CLOTHING, FOOD, BOOKS, OTHER)
- New products are active by default

### Stock Management
- `updateStock(id, delta)` adjusts quantity by delta
- Positive delta = restock, negative delta = reduce
- If resulting stock < 0 → throw InsufficientStockException (400)
- Stock quantity can never go negative

### Deactivation
- Soft delete — sets active=false
- Deactivated products not returned in active listing
- Can still be retrieved by ID

### Search
- `searchByName` is case-insensitive substring match

## Implementation Classes

### Must Generate (AI generates these):
- `ProductServiceImpl` — implements ProductService, @Service
- `ProductController` — @RestController, @RequestMapping("/api/products")

### Cannot Modify (contracts — human-authored):
- `Product` entity
- `ProductDTO`, `CreateProductRequest`, `UpdateProductRequest`
- `ProductRepository`
- `ProductService` interface
- `ProductMapper`
- `ProductCategory` enum
- `InsufficientStockException`
