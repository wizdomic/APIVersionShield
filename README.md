# ApiVersionShield 🛡️

> **API contract validation microservice** — detects breaking changes between API schema versions before deployment and returns a deployment decision.

Built with **Java 21 + Spring Boot 3.2 + PostgreSQL**

---

## What It Does

When deploying a new API version, ApiVersionShield compares its JSON schema against the previous version and returns one of three decisions:

| Decision | Meaning |
|---|---|
| `SAFE_TO_DEPLOY` | No changes detected — fully backward compatible |
| `WARN_ONLY` | New optional/required fields added — proceed with caution |
| `BLOCK_DEPLOYMENT` | Breaking changes detected — do not deploy |

### What Counts as a Breaking Change

| Change | Decision |
|---|---|
| Required field removed | BLOCK |
| Field type changed (e.g. string → integer) | BLOCK |
| Field format changed (e.g. email → date) | BLOCK |
| Property removed | BLOCK |
| New required field added | WARN |
| New optional property added | WARN |
| No changes | SAFE |

---

## Tech Stack

- **Java 21** — virtual threads ready
- **Spring Boot 3.2.1** — web, JPA, security, validation
- **PostgreSQL** — JSONB schema storage
- **Spring Security** — API key authentication
- **networknt json-schema-validator** — JSON Schema draft-07
- **Swagger UI** — interactive API docs
- **JUnit 5 + Mockito** — unit and integration tests
- **Docker** — containerized deployment

---

## Quick Start

### Prerequisites
- Docker & Docker Compose (recommended)
- OR Java 21+ with PostgreSQL for local dev

### 1. Clone

```bash
git clone https://github.com/wizdomic/APIVersionShield.git
cd ApiVersionShield
```

### 2. Configure

```bash
cp .env.example .env
```

Edit `.env` with your values:

```env
DB_URL=jdbc:postgresql://db:5432/ApiVersionShield
DB_USERNAME=postgres
DB_PASSWORD=yourpassword
DB_DRIVER=org.postgresql.Driver
DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
ApiVersionShield_API_KEY=your-secret-key
```

### 3. Run with Docker Compose

```bash
docker-compose up --build
```

App starts at `http://localhost:8080`

### 4. Run locally (no Docker)

```bash
export $(cat .env | xargs)
./mvnw spring-boot:run
```

---

## API Reference

All endpoints require the `X-API-Key` header.

### Upload a Contract

```
POST /contracts/upload
```

```bash
curl -X POST http://localhost:8080/contracts/upload \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-key" \
  -d '{
    "version": "1.0.0",
    "schema": {
      "type": "object",
      "required": ["name", "email"],
      "properties": {
        "name": {"type": "string"},
        "email": {"type": "string", "format": "email"}
      }
    }
  }'
```

**Response:** `201 Created`

---

### Run a Guard Check

```
POST /guard/check
```

```bash
curl -X POST http://localhost:8080/guard/check \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-key" \
  -d '{"from": "1.0.0", "to": "2.0.0"}'
```

**Response:**
```json
{
  "decision": "BLOCK_DEPLOYMENT",
  "reason": "Breaking changes detected",
  "changes": [
    "Required field removed: 'email'",
    "Property removed: 'email'"
  ]
}
```

---

### List All Contracts

```
GET /contracts
```

---

### View Audit Log

```
GET /guard/audit
```

Returns full history of all guard checks with decisions, timestamps, and changes.

---

## Swagger UI

Interactive API docs at:

```
http://localhost:8080/swagger-ui/index.html
```

Click **Authorize** → enter your API key → test all endpoints.

---

## Running Tests

```bash
./mvnw test
```

- **Unit tests** — service logic with Mockito
- **Integration tests** — full Spring context with H2 in-memory DB

---

## Project Structure

```
src/
├── main/java/com/ApiVersionShield/
│   ├── audit/              # GuardAuditLog entity + repository
│   ├── config/             # JacksonConfig (ObjectMapper bean)
│   ├── controller/         # ContractController, GuardController
│   ├── exception/          # GlobalExceptionHandler
│   ├── model/              # ApiContract, DTOs, DeploymentDecision enum
│   ├── repository/         # ApiContractRepository
│   ├── security/           # ApiKeyFilter, SecurityConfig
│   └── service/            # ContractService (core logic)
└── test/java/com/ApiVersionShield/
    ├── controller/         # Integration tests (MockMvc + H2)
    └── service/            # Unit tests (Mockito)
```

---

## Security

All endpoints protected with `X-API-Key` header auth via Spring Security. Swagger UI excluded from auth. API keys loaded from environment variables only — never hardcoded.

---

## Scaling Considerations

- **Pagination** — `/contracts` and `/audit` need pagination at scale
- **Caching** — hot contract lookups could be cached in Redis
- **Async audit** — audit log writes could be async to reduce latency
- **Distributed locking** — concurrent checks on same version pair need coordination in multi-instance deployments

---
