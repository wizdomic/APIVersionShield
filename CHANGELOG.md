# Changelog

All notable changes to APIGuard will be documented here.

---

## v1.0.0 — 2026-08-31

### Added
- Multi-project API contract guard — one instance, many projects
- Auto-baseline management — no manual version tracking needed
- SAFE / WARN / BLOCK deployment decisions
- Force accept breaking changes via `POST /contracts/accept`
- Full project isolation — projects never compare against each other
- API key authentication via `X-API-Key` header
- Manual contract upload via `POST /contracts/upload`
- Payload validation via `POST /contracts/validate`
- Docker and Docker Compose support
- GitHub Actions CI/CD pipeline
- Supabase, Neon, Railway and self-hosted PostgreSQL support

### Known Limitations
- Audit log temporarily disabled — re-enabled in v1.1.0

---

## v1.1.0 — coming soon

- Audit log re-enabled with full project-aware support
- `GET /guard/audit?projectId=` endpoint
- Full history of all guard checks per project
