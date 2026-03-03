# Order Management Feature Specification

## Overview
A REST API for managing orders with complex pricing, discount tiers, state machine transitions, and cross-service user validation.

## Base Path
`/api/orders`

## Business Rules

### User Validation
- User MUST exist (throw `ResourceNotFoundException` if not)
- User MUST be active (throw `IllegalStateException` if inactive)

### Pricing
- `totalAmount` = sum of all `(quantity × unitPrice)` for each item
- Discount tiers:
  - totalAmount < $100 → **0% discount**
  - totalAmount >= $100 → **10% discount**
  - totalAmount >= $500 → **20% discount**
- `finalAmount` = `totalAmount - (totalAmount × discountPercent / 100)`

### State Machine
```
PENDING → CONFIRMED → SHIPPED → DELIVERED
  ↓           ↓
CANCELLED  CANCELLED
```
- PENDING can go to CONFIRMED or CANCELLED
- CONFIRMED can go to SHIPPED or CANCELLED
- SHIPPED can go to DELIVERED only
- DELIVERED and CANCELLED are terminal states
- Invalid transitions throw `InvalidOrderStateException`

## Endpoints

### POST /api/orders
Create a new order.
- **Request Body:** `CreateOrderRequest` (userId, items[], shippingAddress)
- **Response:** `201 Created` with OrderDTO
- **Errors:** `404` user not found, `400` inactive user

### GET /api/orders/{id}
Get order by ID.
- **Response:** `200 OK` with OrderDTO
- **Error:** `404` order not found

### GET /api/orders/user/{userId}
Get all orders for a user.
- **Response:** `200 OK` with list of OrderDTO

### GET /api/orders/status/{status}
Get orders by status.
- **Response:** `200 OK` with list of OrderDTO

### PUT /api/orders/{id}/status
Update order status.
- **Request Param:** `status` (OrderStatus)
- **Response:** `200 OK` with updated OrderDTO
- **Error:** `400` invalid transition, `404` order not found

### PUT /api/orders/{id}/cancel
Cancel an order.
- **Response:** `200 OK` with cancelled OrderDTO
- **Error:** `400` cannot cancel shipped/delivered, `404` order not found

## Implementation Classes Required
1. `OrderServiceImpl` — implements `OrderService`, uses `UserRepository` + `OrderRepository` + `OrderMapper`
2. `OrderController` — REST controller at `/api/orders`
3. Update `GlobalExceptionHandler` — add `InvalidOrderStateException` → 400 and `IllegalStateException` → 400
