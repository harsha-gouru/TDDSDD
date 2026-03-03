# Project Constitution — Rules for AI Agents

## Non-Negotiable Principles

### Code Quality
- All code MUST follow Java naming conventions (camelCase methods, PascalCase classes)
- All classes MUST have proper package declarations matching directory structure
- Use `@Service`, `@RestController`, `@Component` annotations appropriately
- NO raw types — always use generics properly
- NO suppressing warnings without justification

### Architecture
- Follow layered architecture: Controller → Service → Repository
- Controllers MUST NOT contain business logic — delegate to services
- Services MUST use constructor injection (NOT field injection with @Autowired)
- Use the provided `UserMapper` for entity↔DTO conversion — do NOT create ad-hoc mappings

### Testing Compatibility
- Generated implementations MUST pass ALL pre-existing tests WITHOUT modifying test files
- NEVER modify test files — tests define the contract
- NEVER modify contract files (DTOs, interfaces, entities, enums, mappers, exceptions)
- If a test expects a specific behavior, implement EXACTLY that behavior

### Error Handling
- Use `@RestControllerAdvice` for global exception handling
- `ResourceNotFoundException` → 404 Not Found
- `DuplicateResourceException` → 409 Conflict
- `InvalidOrderStateException` → 400 Bad Request
- `InsufficientStockException` → 400 Bad Request
- Return meaningful error messages

### What You CAN Generate
- `*Impl` classes (service implementations: `UserServiceImpl`, `OrderServiceImpl`, `ProductServiceImpl`)
- `*Controller` classes (REST controllers: `UserController`, `OrderController`, `ProductController`)
- `GlobalExceptionHandler` class
- Additional utility classes if needed

### What You CANNOT Modify
- DTOs (`UserDTO`, `CreateUserRequest`, `UpdateUserRequest`, `OrderDTO`, `OrderItemDTO`, `CreateOrderRequest`, `ProductDTO`, `CreateProductRequest`, `UpdateProductRequest`)
- Entities (`User`, `Order`, `OrderItem`, `Product`)
- Interfaces (`UserService`, `OrderService`, `ProductService`)
- Repositories (`UserRepository`, `OrderRepository`, `ProductRepository`)
- Mappers (`UserMapper`, `OrderMapper`, `ProductMapper`)
- Enums (`UserStatus`, `OrderStatus`, `ProductCategory`)
- Exceptions (`ResourceNotFoundException`, `DuplicateResourceException`, `InvalidOrderStateException`, `InsufficientStockException`)
- Test files
- `pom.xml`
- `application.yml`
