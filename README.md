# APIVersionShield – API Contract Guard for Safe Deployments

## Project Overview

**APIVersionShield** is a Spring Boot–based **API contract validation microservice** designed to prevent breaking API changes during deployments.

It acts as a **pre-deployment guard** in CI/CD pipelines by comparing API versions and blocking deployments that introduce backward-incompatible changes.

### Problem

API version upgrades can silently break existing clients when fields are removed or modified, leading to production failures.

### Solution

APIVersionShield validates versioned API contracts and determines whether a new version is **safe to deploy** or should **block deployment**.

---

## Key Features

* **Versioned API Contracts**
  Upload and manage multiple API versions using JSON Schema.

* **Payload Validation**
  Validate request payloads against a specific API version.

* **Backward Compatibility Guard**
  Compare two API versions and detect breaking changes.

* **CI/CD Deployment Gate**
  Return clear decisions:

    * `SAFE_TO_DEPLOY`
    * `BLOCK_DEPLOYMENT`

* **Standalone or Integrable**
  Can run independently or alongside other microservices.

* **Swagger UI Support**
  Easy manual testing and demonstration.

* **Docker & Docker Compose Ready**
  Simple local and CI execution.

---

## High-Level Architecture

```
CI Pipeline / Developer
        |
        v
  APIVersionShield
        |
        v
Contract Comparison Engine
        |
        v
SAFE_TO_DEPLOY / BLOCK_DEPLOYMENT
```

* Operates **before deployment**
* Does **not intercept runtime traffic**
* Designed for **CI/CD and pre-release checks**

---

## Typical Workflow

1. Upload API contracts (v1, v2, v3…)
2. Validate payloads against a chosen version
3. Run guard checks between versions
4. Receive deployment decision
5. CI pipeline proceeds or fails

---

## CI/CD Pre-Deployment Simulation

```bash
RESPONSE=$(curl -s -X POST http://localhost:8080/guard/check \
  -H "Content-Type: application/json" \
  -d '{ "from": "v2", "to": "v3" }')

echo "$RESPONSE"

if echo "$RESPONSE" | grep -q BLOCK_DEPLOYMENT; then
  echo "❌ Deployment blocked"
  exit 1
else
  echo "✅ Safe to deploy"
fi
```

This simulates how a real CI pipeline would block a release automatically.

---

## Running with Docker Compose

```bash
docker compose up --build
```

* APIVersionShield runs as a container
* Can be extended to run alongside other services
* Same setup works locally and in CI runners

---

## How It Can Be Used

### Standalone Mode

* Developers use Swagger UI
* Manually upload contracts
* Run validation and guard checks

### Integrated Mode

* CI/CD pipeline calls APIVersionShield
* Deployment is gated by contract compatibility
* Services remain loosely coupled

---

## Limitations (Intentional for v1)

* Contracts stored database persistent storage(supabase)
* Manual contract upload
* No authentication or authorization
* JSON payloads only
* CI/CD integration simulated via shell scripts

---

## Future Enhancements

* Authentication and RBAC
* GitHub Actions / Jenkins plugins
* Breaking-change diff visualization
* Multi-service contract registry

---

## Tech Stack

* **Java 21**
* **Spring Boot**
* **Postgresql**
* **JSON Schema (NetworkNT Validator)**
* **Swagger / OpenAPI**
* **Docker & Docker Compose**

---# APIVersionShield
# APIVersionShield
# APIVersionShield
