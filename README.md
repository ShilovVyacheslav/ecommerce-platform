<div align="center">

# Scalable E-Commerce Platform

**Microservices Backend with API Gateway & RSA-Signed JWT Auth**

![Java](https://img.shields.io/badge/Java-21-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg)
![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud%20Gateway-2025.0.2-blueviolet.svg)
![Spring Security](https://img.shields.io/badge/Spring%20Security-OAuth2%20%2F%20JWT-red.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-informational.svg)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg?logo=docker&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-8.14.3-02303A.svg?logo=gradle&logoColor=white)
![CI](https://github.com/ShilovVyacheslav/ecommerce-platform/actions/workflows/ci.yml/badge.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

</div>

---

## | Getting Started

### Prerequisites

*   Docker, Docker Compose
*   Java 21 (optional)
*   PostgreSQL 17+ (optional)

**Clone the repository:**
```bash
git clone https://github.com/ShilovVyacheslav/ecommerce-platform.git
cd ecommerce-platform
```

#### Option 1: Docker (recommended)

If you have a fast machine and stable resources, a single command is enough:
```bash
docker compose up --build -d
```

**If that struggles** — Docker Desktop can choke trying to build three JVM images and start five containers all at once, especially on constrained CPU/network. Bring the stack up in stages instead:

```bash
# 1. Databases first — everything else depends on these being healthy
docker compose up -d postgres-user postgres-payment

# 2. Build each service image separately (one at a time, not in parallel)
docker compose build user-service
docker compose build payment-service
docker compose build api-gateway

# 3. Start services in dependency order
docker compose up -d user-service
docker compose up -d payment-service
docker compose up -d api-gateway
```

#### Default Admin Account:

- Username: `admin`
- Password: `Admin#23`

---

#### Option 2: Manual

Requires a local PostgreSQL instance with a database named `ecommerce-platform`, `postgres`/`postgres` credentials (or update `application-local.yml` in each service to match your own).

Each service runs as a separate blocking process — open **three terminals**, one per service:

**Terminal 1:**
```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :user-service:bootRun
```

Start `user-service` first and give it a few seconds — `payment-service`/`api-gateway` fetch signing keys from it (`/.well-known/jwks.json`) on first use.

**Terminal 2:**
```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :payment-service:bootRun
```

**Terminal 3:**
```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :api-gateway:bootRun
```

---

## | Architecture

| Service | Port | Responsibility |
| :--- | :--- | :--- |
| **api-gateway** | `8080` | Single entry point, request routing, JWT validation at the edge |
| **user-service** | `8081` | Auth (register/login/refresh), user management, JWT issuing via RSA + JWKS |
| **payment-service** | `8082` | Payment processing |

Each service owns its own PostgreSQL database — no shared schema, no shared instance.

---
