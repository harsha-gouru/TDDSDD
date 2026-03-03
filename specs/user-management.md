# User Management Feature Specification

## Overview
A REST API for managing users with CRUD operations, soft-delete support, and duplicate email prevention.

## Base Path
`/api/users`

## Endpoints

### POST /api/users
Create a new user.
- **Request Body:** `{ "name": "string (required)", "email": "string (required, valid email)" }`
- **Response:** `201 Created` with UserDTO body
- **Error:** `409 Conflict` if email already exists (throw `DuplicateResourceException`)

### GET /api/users/{id}
Get a user by ID.
- **Response:** `200 OK` with UserDTO body
- **Error:** `404 Not Found` if user doesn't exist (throw `ResourceNotFoundException`)

### GET /api/users
Get all active users.
- **Response:** `200 OK` with list of UserDTO

### PUT /api/users/{id}
Update a user.
- **Request Body:** `{ "name": "string (required)", "email": "string (required, valid email)" }`
- **Response:** `200 OK` with updated UserDTO body
- **Error:** `404 Not Found` if user doesn't exist

### DELETE /api/users/{id}
Soft-delete a user (set `active = false`, do NOT remove from database).
- **Response:** `204 No Content`
- **Error:** `404 Not Found` if user doesn't exist

## Business Rules
1. Email must be unique across all users
2. Delete is a **soft delete** — sets `active = false`, does not remove the record
3. `getAllActiveUsers` returns ONLY users where `active = true`
4. When updating a user, check that the new email isn't already taken by another user
5. Use `UserMapper` to convert between `User` entity and `UserDTO`

## Data Model
- **UserDTO:** `Long id`, `String name`, `String email`, `boolean active`
- **CreateUserRequest:** `String name`, `String email` (both validated)
- **UpdateUserRequest:** `String name`, `String email` (both validated)
- **User Entity:** `Long id`, `String name`, `String email`, `boolean active`, `LocalDateTime createdAt`, `LocalDateTime updatedAt`

## Error Handling
- `ResourceNotFoundException` → HTTP 404
- `DuplicateResourceException` → HTTP 409
- Handle these via a `@RestControllerAdvice` global exception handler

## Implementation Classes Required
1. `UserServiceImpl` — implements `UserService` interface, annotated with `@Service`
2. `UserController` — REST controller with `@RestController` and `@RequestMapping("/api/users")`
3. `GlobalExceptionHandler` — `@RestControllerAdvice` for centralized error handling
