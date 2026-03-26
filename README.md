# Spring Boot E-commerce Backend (Assignment Scope)

Backend for an e-commerce platform using Spring Boot, Spring Security (JWT), Spring Data JPA, and Stripe.

## Features

- JWT authentication (register/login)
- BCrypt password hashing
- Role-based authorization (`USER`, `ADMIN`)
- Product CRUD (`ADMIN` write, public read)
- User-specific cart operations
- Order creation from cart
- Payment flow with Stripe checkout + webhook confirmation
- Global exception handling + request validation
- Swagger/OpenAPI
- Unit tests for auth, cart, order, and payment services
- H2 in-memory database

## Run locally

```bash
mvn clean spring-boot:run
```

## H2 Console

- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:ecommerce_db`
- Username: `sa`
- Password: (empty)

## API base endpoints

- `/api/auth`
- `/api/products`
- `/api/cart`
- `/api/orders`
- `/api/payments`

## Swagger

- `http://localhost:8080/swagger-ui.html`

## Docker

```bash
docker compose up --build
```

## Notes

- Product list supports pagination/filter/sort via query params.
- Order cannot be created from an empty cart.
- Cart duplicate product adds increase quantity.
- Stock is reduced on order creation and restored on payment failure.
