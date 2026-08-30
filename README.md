<div align="center">

# ecommerce-platform

**Scalable E-Commerce Microservices**

![Java](https://img.shields.io/badge/Java-21-blue.svg?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg?logo=springboot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.2-blueviolet.svg?logo=spring&logoColor=white)
![Eureka](https://img.shields.io/badge/Service%20Discovery-Eureka-6DB33F?logo=spring&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-OAuth2%20%2F%20JWT-red.svg?logo=springsecurity&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-informational.svg?logo=postgresql&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-7.0-47A248?logo=mongodb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.0-orange?logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg?logo=docker&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-8.14.3-02303A.svg?logo=gradle&logoColor=white)
[![CI](https://github.com/ShilovVyacheslav/ecommerce-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ShilovVyacheslav/ecommerce-platform/actions/workflows/ci.yml)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

</div>

---

## | Getting Started

**Clone the repository:**
```bash
git clone https://github.com/ShilovVyacheslav/ecommerce-platform.git
cd ecommerce-platform
```

### Option 1: Docker (recommended)

**First-time setup** — create your local env file from the template:

**Linux / macOS:**
```bash
cp .env.docker.example .env.docker
```
**Windows (PowerShell):**
```bash
Copy-Item .env.docker.example .env.docker
```

If you have a fast machine and stable resources, a single command is enough:
```bash
docker compose --env-file .env.docker up --build -d
```

**If that struggles** — Docker Desktop can choke trying to build six JVM images and start 10+ containers all at once, especially on constrained CPU/network. Bring the stack up in stages instead:

```bash
# 1. Infrastructure first — everything else depends on these being healthy
docker compose --env-file .env.docker up -d postgres-user postgres-payment postgres-order mongo-product redis discovery-server

# 2. Build each service image one by one
docker compose --env-file .env.docker build user-service
docker compose --env-file .env.docker build payment-service
docker compose --env-file .env.docker build product-service
docker compose --env-file .env.docker build order-service
docker compose --env-file .env.docker build api-gateway

# 3. Start services in dependency order — user-service first
docker compose --env-file .env.docker up -d user-service
docker compose --env-file .env.docker up -d payment-service
docker compose --env-file .env.docker up -d product-service
docker compose --env-file .env.docker up -d order-service
docker compose --env-file .env.docker up -d api-gateway
```

---

### Option 2: Manual

Requires locally running:
- **PostgreSQL** — one instance, database named `ecommerce-platform`, `postgres`/`postgres` credentials, or update `application-local.yml` in each service to match your own;
- **MongoDB** — one instance, no auth required for the `local` profile;
- **Redis** — one instance, no auth required for the `local` profile;

**Generate a local JWT keypair:**

**Linux / macOS:**
```bash
./scripts/generate-jwt-keys.sh local
```
**Windows (PowerShell):**
```bash
.\scripts\generate-jwt-keys.ps1 local
```

**Terminal 1 — redis**:

```bash
# Linux
redis-server
```
```bash
# macOS
brew services start redis
```
```bash
# Windows
redis-server.exe
```

**macOS / Linux:** `./gradlew`

**Windows (PowerShell, cmd):** `.\gradlew.bat`

**Terminal 2 — discovery-server** (no `local` profile needed):
```bash
./gradlew :discovery-server:bootRun
```
```bash
.\gradlew.bat :discovery-server:bootRun
```

**Terminal 3 — user-service:**
```bash
./gradlew :user-service:bootRun --args='--spring.profiles.active=local'
```
```bash
.\gradlew.bat :user-service:bootRun --args='--spring.profiles.active=local'
```

**Terminal 4 — payment-service:**
```bash
./gradlew :payment-service:bootRun --args='--spring.profiles.active=local'
```
```bash
.\gradlew.bat :payment-service:bootRun --args='--spring.profiles.active=local'
```

**Terminal 5 — product-service:**
```bash
./gradlew :product-service:bootRun --args='--spring.profiles.active=local'
```
```bash
.\gradlew.bat :product-service:bootRun --args='--spring.profiles.active=local'
```

**Terminal 6 — order-service:**
```bash
./gradlew :order-service:bootRun --args='--spring.profiles.active=local'
```
```bash
.\gradlew.bat :order-service:bootRun --args='--spring.profiles.active=local'
```

**Terminal 7 — api-gateway** (start last — it resolves `lb://` against the other five):
```bash
./gradlew :api-gateway:bootRun --args='--spring.profiles.active=local'
```
```bash
.\gradlew.bat :api-gateway:bootRun --args='--spring.profiles.active=local'
```

---

#### Default Admin Account

- Username: `admin`
- Password: `Admin#23`

---

## | Architecture

| Service | Port | Responsibility |
| :--- | :--- | :--- |
| **discovery-server** | `8761` | Eureka service registry |
| **api-gateway** | `8080` | Single entry point, request routing (`lb://`), JWT validation at the edge, Redis-backed rate limiting |
| **user-service** | `8081` | Auth (register/login/refresh), user management, JWT issuing via RSA + JWKS |
| **payment-service** | `8082` | Payment processing |
| **product-service** | `8083` | Product catalog |
| **order-service** | `8084` | Order management |

---