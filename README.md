# APIGuard 🛡️

> A self-hosted API contract validation service. One instance, many projects. Detects breaking changes automatically and blocks bad deployments in CI/CD.

---

## ⚠️ Current Status

| Feature | Status |
|---|---|
| Multi-project contract guard | ✅ Active |
| Auto-baseline management | ✅ Active |
| SAFE / WARN / BLOCK decisions | ✅ Active |
| Force accept breaking changes | ✅ Active |
| Project isolation | ✅ Active |
| Audit log | 🚧 Temporarily disabled — coming back in next release |

---

## How It Works

```
POST /guard/check  { projectId, schema }
         ↓
No baseline exists?  →  store schema as baseline  →  SAFE_TO_DEPLOY
Baseline exists?     →  compare against baseline
                     →  SAFE_TO_DEPLOY   →  new schema becomes baseline
                     →  WARN_ONLY        →  new schema becomes baseline
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

## Database Options

APIGuard works with any PostgreSQL instance. Choose one:

---

### Option A — Docker Compose (built-in PostgreSQL)

The default setup spins up a local PostgreSQL container alongside the app.

```bash
cp .env.example .env
```

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

The `db:5432` hostname refers to the PostgreSQL container inside Docker Compose. No external DB needed.

---

### Option B — Supabase

1. Go to [supabase.com](https://supabase.com) → create a project
2. Go to **Settings → Database → Connection string → URI**
3. Copy the connection string — it looks like:

```
postgresql://postgres.[ref]:[password]@aws-0-ap-south-1.pooler.supabase.com:6543/postgres
```

4. Convert it to JDBC format for your `.env`:

```env
DB_URL=jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres?sslmode=require
DB_USERNAME=postgres.[your-ref]
DB_PASSWORD=your-supabase-db-password
DB_DRIVER=org.postgresql.Driver
DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
APIGUARD_API_KEY=your-secret-api-key
```

> **Note:** Use port `6543` (transaction pooler), not `5432`. Add `?sslmode=require` to the URL.

5. Since you're using an external DB, run only the app — not the full compose stack:

```bash
docker-compose up app --build
```

Or run locally:

```bash
export $(cat .env | xargs) && ./mvnw spring-boot:run
```

---

### Option C — Any other PostgreSQL

Works with Railway, Render, Neon, AWS RDS, or any self-hosted PostgreSQL.

```env
DB_URL=jdbc:postgresql://your-host:5432/your-database?sslmode=require
DB_USERNAME=your-username
DB_PASSWORD=your-password
DB_DRIVER=org.postgresql.Driver
DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
APIGUARD_API_KEY=your-secret-api-key
```

APIGuard uses `spring.jpa.hibernate.ddl-auto=update` — it creates tables automatically on first startup. No manual SQL setup needed.

---

## Setup (5 minutes)

```bash
git clone https://github.com/YOUR_USERNAME/apiguard.git
cd apiguard
cp .env.example .env
# edit .env with your chosen database option above
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

---

## API Reference

All endpoints require `X-API-Key` header.

### Guard Check

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

---

### Force Accept a Breaking Change

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

## Environment Variables Reference

| Variable | Description | Example |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://db:5432/apiguard` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `strongpassword` |
| `DB_DRIVER` | JDBC driver class | `org.postgresql.Driver` |
| `DB_DIALECT` | Hibernate dialect | `org.hibernate.dialect.PostgreSQLDialect` |
| `APIGUARD_API_KEY` | Secret key for all API requests | `my-secret-key` |

---

## License

MIT
