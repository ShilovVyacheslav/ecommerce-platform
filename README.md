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

**Default Admin Account**

- Username: `admin`
- Password: `Admin#23`

**Clone the repository:**
```bash
git clone https://github.com/ShilovVyacheslav/ecommerce-platform.git
cd ecommerce-platform
```

### Option 1: Docker (recommended)

**First-time setup:**

<table>
<colgroup><col style="width: 50%"><col style="width: 50%"></colgroup>
<tr><th>bash / zsh / sh</th><th>PowerShell</th></tr>
<tr><td>

```bash
cp .env.example .env.docker
```

</td><td>

```powershell
Copy-Item .env.example .env.docker
```

</td></tr>
</table>

If you have a fast machine and stable resources, a single command is enough:
```bash
docker compose --env-file .env.docker up --build -d
```

**If that struggles** — Docker Desktop can choke trying to build six JVM images and start 10+ containers all at once, especially on constrained CPU/network. Bring the stack up in stages instead:

```bash
# 1. Infrastructure first — everything else depends on these being healthy
docker compose --env-file .env.docker up -d postgres-user \
  postgres-payment postgres-order mongo-product redis discovery-server

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

**Shut down:**

```bash
docker compose --env-file .env.docker down
```

---

### Option 2: Manual

Requires locally running:
- **PostgreSQL** — one instance, database named `ecommerce-platform`, `postgres`/`postgres` credentials, or update `application-local.yml` in each service to match your own;
- **MongoDB** — one instance, no auth required for the `local` profile;
- **Redis** — one instance, no auth required for the `local` profile;

**Generate a local JWT keypair:**

<table>
<colgroup><col style="width: 50%"><col style="width: 50%"></colgroup>
<tr><th>bash / zsh / sh</th><th>PowerShell</th></tr>
<tr><td>

```bash
./scripts/generate-jwt-keys.sh
```

</td><td>

```powershell
.\scripts\generate-jwt-keys.ps1
```

</td></tr>
</table>

**Terminal 1 — redis**:

<table>
<colgroup><col style="width: 50%"><col style="width: 50%"></colgroup>
<tr><th>bash / zsh / sh</th><th>PowerShell</th></tr>
<tr><td>

```bash
# macOS
brew services start redis
```
```bash
redis-server
```

</td><td>

```powershell
cd C:\Path\To\Redis
.\redis-server.exe
```

</td></tr>
</table>

**Terminal 2 — discovery-server** (no `local` profile needed):

<table>
<tr><td><strong>bash / zsh / sh</strong></td>
<td>

```bash
./gradlew :discovery-server:bootRun
```

</td></tr>
<tr><td><strong>PowerShell / cmd</strong></td>
<td>

```powershell
.\gradlew.bat :discovery-server:bootRun
```

</td></tr>
</table>

**Terminal 3 — user-service:**

<table>
<tr><td><strong>bash / zsh / sh</strong></td>
<td>

```bash
./gradlew :user-service:bootRun --args='--spring.profiles.active=local'
```

</td></tr>
<tr><td><strong>PowerShell / cmd</strong></td>
<td>

```powershell
.\gradlew.bat :user-service:bootRun --args='--spring.profiles.active=local'
```

</td></tr>
</table>

**Terminal 4 — payment-service:**

<table>
<tr><td><strong>bash / zsh / sh</strong></td>
<td>

```bash
./gradlew :payment-service:bootRun --args='--spring.profiles.active=local'
```

</td></tr>
<tr><td><strong>PowerShell / cmd</strong></td>
<td>

```powershell
.\gradlew.bat :payment-service:bootRun --args='--spring.profiles.active=local'
```

</td></tr>
</table>

**Terminal 5 — product-service:**

<table>
<tr><td><strong>bash / zsh / sh</strong></td>
<td>

```bash
./gradlew :product-service:bootRun --args='--spring.profiles.active=local'
```

</td></tr>
<tr><td><strong>PowerShell / cmd</strong></td>
<td>

```powershell
.\gradlew.bat :product-service:bootRun --args='--spring.profiles.active=local'
```

</td></tr>
</table>

**Terminal 6 — order-service:**

<table>
<tr><td><strong>bash / zsh / sh</strong></td>
<td>

```bash
./gradlew :order-service:bootRun --args='--spring.profiles.active=local'
```

</td></tr>
<tr><td><strong>PowerShell / cmd</strong></td>
<td>

```powershell
.\gradlew.bat :order-service:bootRun --args='--spring.profiles.active=local'
```

</td></tr>
</table>

**Terminal 7 — api-gateway** (start last — it resolves `lb://` against the other five):

<table>
<tr><td><strong>bash / zsh / sh</strong></td>
<td>

```bash
./gradlew :api-gateway:bootRun --args='--spring.profiles.active=local'
```

</td></tr>
<tr><td><strong>PowerShell / cmd</strong></td>
<td>

```powershell
.\gradlew.bat :api-gateway:bootRun --args='--spring.profiles.active=local'
```

</td></tr>
</table>

---

### Option 3: Kubernetes

Requires [minikube](https://minikube.sigs.k8s.io/docs/start/) and `kubectl` installed, plus Docker as minikube's driver (see the note below if Docker doesn't run natively on your machine).

> **Resource requirements** — the full stack (6 services, 3 Postgres instances, a sharded MongoDB cluster, Redis, plus the Kubernetes control plane itself) needs realistically **8GB+ RAM** to run comfortably. On less, expect pods stuck in `Pending`/`OOMKilled` — this isn't a bug, it's the cluster running out of room. `free -h` / `kubectl top pods` are your friends if something won't start.

**Start the cluster:**
```bash
minikube start --driver=docker --cpus=4 --memory=8192
minikube addons enable ingress
```

**Build the images into minikube's own Docker daemon** (so it doesn't need to pull from a registry):

<table>
<tr><th>bash (Linux / macOS / Git Bash / WSL)</th><th>PowerShell</th></tr>
<tr><td>

```bash
eval $(minikube docker-env)
docker compose build user-service payment-service product-service order-service api-gateway
```

</td><td>

```powershell
& minikube -p minikube docker-env --shell powershell | Invoke-Expression
docker compose build user-service payment-service product-service order-service api-gateway
```

</td></tr>
</table>

`discovery-server` is intentionally not built — Kubernetes' own DNS replaces Eureka in this profile, no discovery server is deployed.

**Namespace and secrets:**
```bash
kubectl apply -f k8s/00-namespace.yaml
./scripts/k8s-create-secrets.sh
```
`k8s-create-secrets.sh` is bash-only. On Windows, run it from Git Bash — the rest of the commands below work the same from PowerShell or bash.

**Bring up the infrastructure, waiting on each layer before the next:**
```bash
kubectl apply -f k8s/postgres-user.yaml -f k8s/postgres-payment.yaml -f k8s/postgres-order.yaml -f k8s/redis.yaml
kubectl wait --for=condition=ready pod -l app=postgres-user -n ecommerce --timeout=120s
kubectl wait --for=condition=ready pod -l app=postgres-payment -n ecommerce --timeout=120s
kubectl wait --for=condition=ready pod -l app=postgres-order -n ecommerce --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n ecommerce --timeout=120s
```

**MongoDB sharded cluster — must come up in order:**
```bash
kubectl apply -f k8s/mongo-configsvr.yaml
kubectl wait --for=condition=ready pod -l app=mongo-configsvr -n ecommerce --timeout=180s

kubectl apply -f k8s/mongo-shard1.yaml -f k8s/mongo-shard2.yaml
kubectl wait --for=condition=ready pod -l app=mongo-shard1 -n ecommerce --timeout=180s
kubectl wait --for=condition=ready pod -l app=mongo-shard2 -n ecommerce --timeout=180s

kubectl apply -f k8s/mongo-mongos.yaml
kubectl wait --for=condition=ready pod -l app=mongos -n ecommerce --timeout=180s

kubectl apply -f k8s/mongo-init-job.yaml
kubectl wait --for=condition=complete job/mongo-cluster-init -n ecommerce --timeout=300s
```
If the last `wait` times out, check what went wrong before continuing:
```bash
kubectl logs job/mongo-cluster-init -n ecommerce
```

**Business services:**
```bash
kubectl apply -f k8s/user-service.yaml
kubectl wait --for=condition=ready pod -l app=user-service -n ecommerce --timeout=180s

kubectl apply -f k8s/payment-service.yaml -f k8s/product-service.yaml -f k8s/order-service.yaml -f k8s/api-gateway.yaml
kubectl get pods -n ecommerce -w
```
Wait until everything shows `1/1 Running`, then `Ctrl+C`.

**Access the API:**
```bash
kubectl apply -f k8s/ingress.yaml
minikube service api-gateway -n ecommerce --url
```
That URL is a direct route to `api-gateway`, equivalent to `http://localhost:8080` in the other two options.

**Troubleshooting a pod that won't come up:**
```bash
kubectl get pods -n ecommerce
kubectl describe pod <pod-name> -n ecommerce
kubectl logs <pod-name> -n ecommerce
```

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