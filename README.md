# APIGuard

**API Contract Validation Microservice**  
Java 21 · Spring Boot 3.2 · PostgreSQL

APIGuard is a deployment gate microservice that compares JSON schemas between API versions and returns a structured deployment decision:

- ✅ `SAFE_TO_DEPLOY`
- ⚠️ `WARN_ONLY`
- ❌ `BLOCK_DEPLOYMENT`

This ensures breaking API changes are detected **before** a release goes live.

---

## 🚀 Overview

APIGuard performs deep JSON schema comparisons to detect breaking changes such as:

- Required field removal
- Property type changes (e.g., `string → integer`)
- Format changes (e.g., `email → date`)

All violations are collected before returning a final deployment decision.

---

## 🧠 Core Features

### Schema Diff Engine
- Deep comparison of JSON schemas
- Detects breaking and non-breaking changes
- Aggregates all violations before computing final status

### Deployment Decision Engine
Returns one of:
- `SAFE_TO_DEPLOY`
- `WARN_ONLY`
- `BLOCK_DEPLOYMENT`

### Audit Trail
Every guard check is persisted to PostgreSQL, including:
- API versions compared
- Decision result
- Detailed reasoning
- Timestamp

All records are queryable via REST endpoints.

### Security
- API key authentication
- Stateless `OncePerRequestFilter`
- Designed for machine-to-machine CI/CD environments
- Implemented using Spring Security

---

## 🧪 Testing

### Unit Tests
- Mockito
- Covers all deployment decision paths

### Integration Tests
- `@SpringBootTest`
- MockMvc for full HTTP stack testing
- H2 in-memory database for isolated test runs

---

## 🏗 Production-Ready Patterns

- `@Transactional` on all service methods
  - `readOnly = true` for read operations
- Constructor-based dependency injection
- Environment variable-based configuration
- Structured logging with SLF4J

---

## 🛠 Tech Stack

- Java 21
- Spring Boot 3.2
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL (JSONB)
- H2 (testing)
- Mockito
- MockMvc
- networknt JSON Schema Validator
- Maven
- Git
- Swagger / OpenAPI
- Lombok
- Supabase

---

## 💻 Technical Skills

| Category     | Skills |
|--------------|--------|
| **Languages** | Java 21, SQL |
| **Frameworks** | Spring Boot, Spring Security, Spring Data JPA, Hibernate |
| **Databases** | PostgreSQL (JSONB), H2 |
| **Testing** | JUnit 5, Mockito, MockMvc, `@SpringBootTest` |
| **Tools** | Maven, Git, Swagger/OpenAPI, Lombok, Supabase |

--- 