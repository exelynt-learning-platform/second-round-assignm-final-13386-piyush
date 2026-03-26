<<<<<<< HEAD
# Spring Boot E-commerce Backend

Complete e-commerce backend built with Spring Boot, Spring Security (JWT), Spring Data JPA, and Stripe checkout integration.

## Features

- JWT authentication with BCrypt password hashing
- Role-based access (`USER`, `ADMIN`)
- Product CRUD (admin write, public read)
- User cart management (one cart per user)
- Order creation from cart with stock deduction
- Stripe checkout session creation
- Payment success/failure handling + webhook handling
- Global validation and exception handling
- Swagger/OpenAPI docs
- Unit tests (Auth, Cart, Order)
- Docker + PostgreSQL support

## Tech Stack

- Java 17
- Spring Boot 3.3.x
- Spring Security
- Spring Data JPA
- PostgreSQL
- JWT (`jjwt`)
- Stripe Java SDK
- JUnit 5 + Mockito

## Project Structure

```text
src/main/java/com/ecommerce/backend
  config/
  controller/
  dto/
  entity/
  exception/
  mapper/
  repository/
  security/
  service/
```

## Step-by-Step Setup

### 1) Configure Environment Variables

Required:

- `DB_URL` (default: `jdbc:postgresql://localhost:5432/ecommerce_db`)
- `DB_USERNAME` (default: `postgres`)
- `DB_PASSWORD` (default: `postgres`)
- `JWT_SECRET` (minimum 32 bytes)
- `STRIPE_SECRET_KEY` (for payment session creation)
- `STRIPE_WEBHOOK_SECRET` (for webhook verification)

Optional:

- `APP_SEED_ADMIN_EMAIL`
- `APP_SEED_ADMIN_PASSWORD`

### 2) Run with Maven

```bash
mvn clean spring-boot:run
```

### 3) Run with Docker

```bash
docker compose up --build
```

## Swagger

- URL: `http://localhost:8080/swagger-ui.html`

## Core API Endpoints

### Auth

- `POST /api/auth/register`
- `POST /api/auth/login`

### User

- `GET /api/users/me`

### Products

- `GET /api/products`
- `GET /api/products/{id}`
- `POST /api/products` (ADMIN)
- `PUT /api/products/{id}` (ADMIN)
- `DELETE /api/products/{id}` (ADMIN)

### Cart

- `GET /api/cart`
- `POST /api/cart/items`
- `PUT /api/cart/items/{itemId}`
- `DELETE /api/cart/items/{itemId}`

### Orders

- `POST /api/orders`
- `GET /api/orders`
- `GET /api/orders/{orderId}`

### Payments

- `POST /api/payments/session`
- `POST /api/payments/{orderId}/success?sessionId=...`
- `POST /api/payments/{orderId}/failure?sessionId=...`
- `POST /api/payments/webhook`

## Authentication Header

```text
Authorization: Bearer <jwt-token>
```

## Notes

- Product listing supports pagination/filtering:
  - `page`, `size`, `sortBy`, `sortDir`, `keyword`, `minPrice`, `maxPrice`
- Register API creates a user with role `USER` by default.
- Admin endpoints require `ROLE_ADMIN`.

## Test Scope

Unit tests cover:

- Authentication service (`AuthServiceTest`)
- Cart operations (`CartServiceTest`)
- Order creation (`OrderServiceTest`)
=======
# second-round-assignm-final-13386-piyush
Final Project Assignment - This repository contains the complete final project code and documentation.
>>>>>>> b2e063f30da03c87eb0e5faea97418408f387d39
