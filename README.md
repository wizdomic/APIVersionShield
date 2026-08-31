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
| Audit log | 🚧 Temporarily disabled — coming in v1.1.0 |

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

## Database Options

Choose one before proceeding.

---

### Option A — Built-in PostgreSQL (Docker Compose)

Spins up a PostgreSQL container alongside the app. No external database needed.

Use `db:5432` as your DB host — it refers to the PostgreSQL container inside Compose.

---

### Option B — Supabase

1. Go to [supabase.com](https://supabase.com) → create a project
2. Go to **Settings → Database → Connection string → URI**
3. Copy the URI — it looks like:
   ```
   postgresql://postgres.[ref]:[password]@aws-0-region.pooler.supabase.com:6543/postgres
   ```
4. Use port `6543` (transaction pooler), not `5432`
5. Add `?sslmode=require` to the URL

Your DB values will be:
```
DB_URL      = jdbc:postgresql://aws-0-region.pooler.supabase.com:6543/postgres?sslmode=require
DB_USERNAME = postgres.[your-ref]
DB_PASSWORD = your-supabase-password
```

---

### Option C — Any Other PostgreSQL

Works with Railway, Neon, Render, AWS RDS, or self-hosted PostgreSQL.

```
DB_URL      = jdbc:postgresql://your-host:5432/your-database
DB_USERNAME = your-username
DB_PASSWORD = your-password
```

Add `?sslmode=require` if your provider requires SSL.

> APIGuard uses `spring.jpa.hibernate.ddl-auto=update` — it creates all tables automatically on first startup. No manual SQL needed.

---

## Setup

### Step 1 — Create project folder

```bash
mkdir apiguard
cd apiguard
```

---

### Step 2 — Create the environment file

Create a file named `.env` inside the folder:

```bash
touch .env
```

Open it and fill in your values based on your chosen database option:

**Option A — Docker Compose PostgreSQL:**
```env
DB_URL=jdbc:postgresql://db:5432/apiguard
DB_USERNAME=postgres
DB_PASSWORD=your-strong-password
DB_DRIVER=org.postgresql.Driver
DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
APIGUARD_API_KEY=your-secret-api-key
```

**Option B — Supabase:**
```env
DB_URL=jdbc:postgresql://aws-0-region.pooler.supabase.com:6543/postgres?sslmode=require
DB_USERNAME=postgres.your-ref
DB_PASSWORD=your-supabase-password
DB_DRIVER=org.postgresql.Driver
DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
APIGUARD_API_KEY=your-secret-api-key
```

**Option C — Other PostgreSQL:**
```env
DB_URL=jdbc:postgresql://your-host:5432/your-database
DB_USERNAME=your-username
DB_PASSWORD=your-password
DB_DRIVER=org.postgresql.Driver
DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
APIGUARD_API_KEY=your-secret-api-key
```

> `APIGUARD_API_KEY` — choose any secret string. This protects all your API endpoints. You will use this same key in your CI/CD pipeline.

---

### Step 3 — Create docker-compose.yml

Create a file named `docker-compose.yml` inside the folder:

```yaml
version: '3.9'

services:
  db:
    image: postgres:16-alpine
    container_name: apiguard-db
    environment:
      POSTGRES_DB: apiguard
      POSTGRES_USER: ${DB_USERNAME:-postgres}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-postgres}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-postgres} -d apiguard"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    image: dev3rahul/apiguard:latest
    container_name: apiguard-app
    ports:
      - "8080:8080"
    environment:
      DB_URL: ${DB_URL}
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      DB_DRIVER: ${DB_DRIVER}
      DB_DIALECT: ${DB_DIALECT}
      APIGUARD_API_KEY: ${APIGUARD_API_KEY}
    depends_on:
      db:
        condition: service_healthy
    restart: on-failure

volumes:
  postgres_data:
```

> **Using Supabase or external PostgreSQL (Option B or C)?**
> Remove the entire `db` service and `volumes` block — you don't need a local PostgreSQL container.
> Also remove `depends_on` from the `app` service.

---

### Step 4 — Start the app

**Option A — Docker Compose PostgreSQL:**
```bash
docker-compose up -d
```

**Option B or C — External PostgreSQL:**
```bash
docker-compose up -d app
```

---

### Step 5 — Verify it's running

```bash
curl http://localhost:8080/swagger-ui/index.html
```

Or open it in your browser. You should see the Swagger UI.

Click **Authorize** → enter your `APIGUARD_API_KEY` → **Authorize** → **Close**

---

## CI/CD Integration

### Step 1 — Add api-schema.json to your project

In the root of your project (not APIGuard — your own project):

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

Update this file whenever your API shape changes.

---

### Step 2 — Add GitHub Actions workflow

Create `.github/workflows/api-guard.yml` in your project:

```yaml
name: API Contract Guard

on:
  push:
    branches: [main]

jobs:
  guard:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v3

      - name: Check API contract
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

      - name: Deploy
        run: echo "your deploy command here"
```

---

### Step 3 — Add GitHub Secrets

Go to your project repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

Add these two:

| Secret | Value |
|---|---|
| `APIGUARD_URL` | `http://your-server-ip:8080` |
| `APIGUARD_API_KEY` | same value as your `.env` |

---

### That's it

From now on, every push to main:
```
push code
    ↓
pipeline runs api-schema.json through APIGuard
    ↓
SAFE or WARN → deploy continues
BLOCK        → pipeline fails, deployment stopped
```

No version tracking. No CURRENT_VERSION. APIGuard manages the baseline internally.

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
    "required": ["name"],
    "properties": {
      "name": {"type": "string"}
    }
  }
}
```

---

### Force Accept a Breaking Change

When you intentionally want a breaking schema as the new baseline:

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

### List Contracts

```
GET /contracts                      ← all projects
GET /contracts?projectId=project-a  ← one project
```

---

### Validate a Payload

```
POST /contracts/validate?projectId=project-a
POST /contracts/validate?projectId=project-a&version=v-abc123
```

Body: raw JSON payload to validate.

---

### Manual Upload

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

## Stop the App

```bash
docker-compose down        # stop containers
docker-compose down -v     # stop + delete database volume
```
