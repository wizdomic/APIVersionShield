# APIGuard 🛡️

> A self-hosted API contract validation microservice. Detects breaking changes between API schema versions before deployment and blocks them automatically in your CI/CD pipeline.

---

## How It Works

```
You upload v1.0.0 schema  →  stored in your database
You upload v2.0.0 schema  →  stored in your database
POST /guard/check          →  APIGuard compares both
                           →  returns SAFE / WARN / BLOCK
```

| Decision | Meaning |
|---|---|
| `SAFE_TO_DEPLOY` | No changes — fully backward compatible |
| `WARN_ONLY` | New fields added — proceed with caution |
| `BLOCK_DEPLOYMENT` | Breaking changes — deployment blocked |

---

## Prerequisites

- Docker and Docker Compose installed
- That's it

---

## Setup (5 minutes)

### 1. Clone the repo

```bash
git clone https://github.com/wizdomic/APIVersionShield.git
```

### 2. Create your environment file

```bash
cp .env.example .env
```

### 3. Open `.env` and fill in your values

```env
DB_URL=jdbc:postgresql://db:5432/apiguard
DB_USERNAME=postgres
DB_PASSWORD=your-strong-password-here
DB_DRIVER=org.postgresql.Driver
DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
APIGUARD_API_KEY=your-secret-api-key-here
```

> **DB_URL** — keep it exactly as above if using Docker Compose. It points to the Postgres container.
>
> **DB_PASSWORD** — choose any strong password.
>
> **APIGUARD_API_KEY** — choose any secret string. This is what protects your API endpoints. Share it only with your team and CI/CD pipeline.

### 4. Start the app

```bash
docker-compose up --build
```

That's it. APIGuard is running at `http://localhost:8080`

---

## Verify It's Working

Open Swagger UI:

```
http://localhost:8080/swagger-ui/index.html
```

Click **Authorize** → enter your `APIGUARD_API_KEY` → **Authorize** → **Close**

---

## Using the API

All requests require the header:
```
X-API-Key: your-secret-api-key-here
```

---

### Upload a contract

```bash
curl -X POST http://localhost:8080/contracts/upload \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-api-key-here" \
  -d '{
    "version": "1.0.0",
    "schema": {
      "type": "object",
      "required": ["userId", "email"],
      "properties": {
        "userId": {"type": "string"},
        "email": {"type": "string", "format": "email"}
      }
    }
  }'
```

Response: `201 Created`

---

### Run a guard check

```bash
curl -X POST http://localhost:8080/guard/check \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-api-key-here" \
  -d '{
    "from": "1.0.0",
    "to": "2.0.0"
  }'
```

Response:
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

### List all contracts

```bash
curl http://localhost:8080/contracts \
  -H "X-API-Key: your-secret-api-key-here"
```

---

### View audit log

```bash
curl http://localhost:8080/guard/audit \
  -H "X-API-Key: your-secret-api-key-here"
```

---

## CI/CD Integration (GitHub Actions)

Add this to your pipeline at `.github/workflows/deploy.yml`:

```yaml
- name: Upload new contract
  run: |
    curl -s -X POST ${{ secrets.APIGUARD_URL }}/contracts/upload \
      -H "Content-Type: application/json" \
      -H "X-API-Key: ${{ secrets.APIGUARD_API_KEY }}" \
      -d '{
        "version": "${{ github.ref_name }}",
        "schema": '"$(cat src/main/resources/api-schema.json)"'
      }'

- name: Guard check — block breaking changes
  run: |
    RESULT=$(curl -s -X POST ${{ secrets.APIGUARD_URL }}/guard/check \
      -H "Content-Type: application/json" \
      -H "X-API-Key: ${{ secrets.APIGUARD_API_KEY }}" \
      -d '{
        "from": "${{ secrets.CURRENT_VERSION }}",
        "to": "${{ github.ref_name }}"
      }')

    DECISION=$(echo $RESULT | jq -r '.decision')

    if [ "$DECISION" = "BLOCK_DEPLOYMENT" ]; then
      echo "❌ Deployment blocked — breaking changes detected"
      echo $RESULT | jq '.changes'
      exit 1
    fi

    echo "✅ $DECISION — safe to deploy"

- name: Deploy
  run: echo "your deploy command here"
```

### GitHub Secrets to configure

Go to your repo → **Settings** → **Secrets and variables** → **Actions** → add:

| Secret | Value |
|---|---|
| `APIGUARD_URL` | `https://your-apiguard-domain.com` |
| `APIGUARD_API_KEY` | same key as your `.env` |
| `CURRENT_VERSION` | your current live version e.g. `1.0.0` |

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

## Stopping the App

```bash
docker-compose down
```

To also delete the database:

```bash
docker-compose down -v
```

---

## Environment Variables Reference

| Variable | Description | Example |
|---|---|---|
| `DB_URL` | PostgreSQL connection URL | `jdbc:postgresql://db:5432/apiguard` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `strongpassword` |
| `DB_DRIVER` | JDBC driver class | `org.postgresql.Driver` |
| `DB_DIALECT` | Hibernate dialect | `org.hibernate.dialect.PostgreSQLDialect` |
| `APIGUARD_API_KEY` | Secret key for API auth | `my-secret-key` |

---

## Using Your Own PostgreSQL (without Docker Compose DB)

If you already have a PostgreSQL instance, update `.env`:

```env
DB_URL=jdbc:postgresql://your-host:5432/your-database
DB_USERNAME=your-username
DB_PASSWORD=your-password
```

Then run only the app container:

```bash
docker run -p 8080:8080 --env-file .env apiguard
```

---

## Running Tests

```bash
./mvnw test
```

---

## License

MIT