# APIGuard 🛡️

> A self-hosted API contract validation service. One instance, many projects. Detects breaking changes automatically and blocks bad deployments in CI/CD.

---

## How It Works

```
POST /guard/check  { projectId, schema }
         ↓
No baseline exists?  →  store schema as baseline  →  SAFE_TO_DEPLOY
Baseline exists?     →  compare against baseline
                     →  SAFE_TO_DEPLOY  →  new schema becomes baseline
                     →  WARN_ONLY       →  new schema becomes baseline
                     →  BLOCK_DEPLOYMENT →  old baseline kept, pipeline fails
```

| Decision | Meaning |
|---|---|
| `SAFE_TO_DEPLOY` | No changes — fully backward compatible |
| `WARN_ONLY` | New fields added — non-breaking, baseline updated |
| `BLOCK_DEPLOYMENT` | Breaking changes — baseline unchanged |

---

## One Instance, Many Projects

```
APIGuard  +  PostgreSQL
              │
    ┌─────────┼─────────┐
    ▼         ▼         ▼
 project-a  project-b  project-c
 (isolated) (isolated) (isolated)
```

Each project has its own independent baseline. Projects never compare against each other.

---

## Prerequisites

- Docker and Docker Compose

---

## Setup (5 minutes)

```bash
git clone https://github.com/YOUR_USERNAME/apiguard.git
cd apiguard
cp .env.example .env
```

Edit `.env`:

```env
DB_URL=jdbc:postgresql://db:5432/apiguard
DB_USERNAME=postgres
DB_PASSWORD=your-strong-password
DB_DRIVER=org.postgresql.Driver
DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
APIGUARD_API_KEY=your-secret-api-key
```

```bash
docker-compose up --build
```

App runs at `http://localhost:8080`

---

## CI/CD Integration

Add `api-schema.json` to your project root:

```json
{
  "type": "object",
  "required": ["userId", "email"],
  "properties": {
    "userId": {"type": "string"},
    "email": {"type": "string", "format": "email"}
  }
}
```

Add to your GitHub Actions pipeline:

```yaml
- name: API Contract Guard
  env:
    APIGUARD_URL: ${{ secrets.APIGUARD_URL }}
    APIGUARD_API_KEY: ${{ secrets.APIGUARD_API_KEY }}
  run: |
    RESULT=$(curl -s -X POST "$APIGUARD_URL/guard/check" \
      -H "X-API-Key: $APIGUARD_API_KEY" \
      -H "Content-Type: application/json" \
      -d '{
        "projectId": "your-project-name",
        "schema": '"$(cat api-schema.json)"'
      }')

    echo "$RESULT"
    DECISION=$(echo "$RESULT" | jq -r '.decision')

    if [ "$DECISION" = "BLOCK_DEPLOYMENT" ]; then
      echo "❌ Breaking API changes detected — deployment blocked"
      echo "$RESULT" | jq '.changes'
      exit 1
    fi

    echo "✅ $DECISION — safe to deploy"
```

GitHub Secrets to configure:

| Secret | Value |
|---|---|
| `APIGUARD_URL` | where APIGuard is running |
| `APIGUARD_API_KEY` | same as your `.env` |

That's it. No version management. No `CURRENT_VERSION`. APIGuard handles the baseline internally.

---

## API Reference

All endpoints require `X-API-Key` header.

### Guard Check (main endpoint)

```
POST /guard/check
```

```json
{
  "projectId": "project-a",
  "schema": {
    "type": "object",
    "required": ["userId", "email"],
    "properties": {
      "userId": {"type": "string"},
      "email": {"type": "string", "format": "email"}
    }
  }
}
```

Response:
```json
{
  "decision": "BLOCK_DEPLOYMENT",
  "reason": "Breaking changes detected",
  "changes": ["Property removed: 'email'"]
}
```

---

### Force Accept a Breaking Change

When you intentionally want a breaking schema to become the new baseline:

```
POST /contracts/accept
```

```json
{
  "projectId": "project-a",
  "schema": { ... }
}
```

---

### Manual Contract Upload

```
POST /contracts/upload
```

```json
{
  "projectId": "project-a",
  "version": "1.0.0",
  "schema": { ... }
}
```

---

### List Contracts

```
GET /contracts                     ← all contracts
GET /contracts?projectId=project-a ← filtered by project
```

---

### Validate a Payload

```
POST /contracts/validate?projectId=project-a
POST /contracts/validate?projectId=project-a&version=1.0.0
```

Validates a request payload against the project baseline (or a specific version).

---

### Audit Log

```
GET /guard/audit                     ← all checks
GET /guard/audit?projectId=project-a ← filtered by project
```

---

## Swagger UI

```
http://localhost:8080/swagger-ui/index.html
```

Click **Authorize** → enter your API key → test all endpoints.

---

## What Counts as a Breaking Change

| Change | Decision |
|---|---|
| Required field removed | BLOCK |
| Field type changed | BLOCK |
| Field format changed | BLOCK |
| Property removed | BLOCK |
| New required field added | WARN |
| New optional property added | WARN |
| No changes | SAFE |

---

## Multiple Projects on One Instance

```
project-a → POST /guard/check  { "projectId": "project-a", "schema": ... }
project-b → POST /guard/check  { "projectId": "project-b", "schema": ... }
project-c → POST /guard/check  { "projectId": "project-c", "schema": ... }
```

All use the same APIGuard URL and API key. Baselines are completely isolated.

---

## Using Your Own PostgreSQL (no Docker Compose DB)

```env
DB_URL=jdbc:postgresql://your-host:5432/your-database
DB_USERNAME=your-username
DB_PASSWORD=your-password
```

Then run only the app container:

```bash
docker run -p 8080:8080 --env-file .env yourusername/apiguard:latest
```

---

## Running Tests

```bash
./mvnw test
```

---

## Stop

```bash
docker-compose down       # stop containers
docker-compose down -v    # stop + delete database volume
```

---

## Environment Variables

| Variable | Description |
|---|---|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `DB_DRIVER` | `org.postgresql.Driver` |
| `DB_DIALECT` | `org.hibernate.dialect.PostgreSQLDialect` |
| `APIGUARD_API_KEY` | Secret key for all API requests |

---

## License

MIT
