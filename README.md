# finance-tracker-api

Spring Boot backend for **Where did my money go** — personal expense tracking with
a rollup-backed analytics layer.

This is phase 1–2 of the architecture document: authentication, the personal
ledger, categories, and the analytics endpoints that feed the dashboard. The
database schema is complete for every later phase (groups, splits, settlements,
recurring payments), so those modules add code but not another migration of the
core tables.

## Running it

```bash
cp .env.example .env      # fill in DATABASE_URL, DATABASE_PASSWORD, APP_JWT_SECRET
set -a; source .env; set +a
mvn spring-boot:run
```

The API comes up on `http://localhost:8080`. Flyway applies `V1__initial_schema.sql`
on first boot, so the Neon database can be empty.

OpenAPI docs: `http://localhost:8080/swagger-ui.html`

## Configuration

| Variable | Meaning |
| --- | --- |
| `DATABASE_URL` | Neon JDBC URL. Use the **pooled** host (`-pooler`) — Hibernate opens more connections than the direct endpoint allows. |
| `DATABASE_USER`, `DATABASE_PASSWORD` | Neon role credentials |
| `APP_JWT_SECRET` | ≥32 characters. The app refuses to start without it. `openssl rand -base64 48` |
| `CORS_ORIGINS` | Comma-separated origins allowed to call the API with credentials |
| `COOKIE_SECURE` | `true` in every deployed environment; `false` only over local http |

## Design notes

**Money is `BIGINT` minor units.** ₹1,250.00 is `125000`. No floating point
anywhere in the money path. The client formats for display; the API never sends
a pre-formatted string.

**Access tokens are JWTs, refresh tokens are not.** The refresh token is an
opaque 48-byte random value, stored as a SHA-256 hash and delivered in an
httpOnly cookie. Presenting an already-rotated token revokes the entire token
family — the standard way to detect a stolen cookie.

**The rollup is written in the same transaction as the expense.** A chart and
the expense list can never disagree, because there is no window in which one
exists without the other.

**Derived expenses are read-only.** Rows with `origin` of `GROUP` or `AUTOPAY`
are owned by the module that created them; `PATCH` and `DELETE` on them return
`403` with the parent id, so the client can offer a link rather than a dead end.

**Keyset pagination, not offset.** `?limit=50&cursor=…`. Offset pagination
degrades once a user has years of expenses, and it skips rows when something is
inserted mid-scroll.

## Endpoints in this phase

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
POST   /api/v1/auth/logout-everywhere

GET    /api/v1/me
PATCH  /api/v1/me

GET    /api/v1/categories
POST   /api/v1/categories
PATCH  /api/v1/categories/{id}
DELETE /api/v1/categories/{id}?reassignTo={id}

GET    /api/v1/expenses          ?from&to&categoryId&tripId&origin&minAmount&maxAmount&q&limit&cursor
POST   /api/v1/expenses          (honours Idempotency-Key)
GET    /api/v1/expenses/{id}
PATCH  /api/v1/expenses/{id}
DELETE /api/v1/expenses/{id}
POST   /api/v1/expenses/{id}/restore

GET    /api/v1/analytics/summary       ?from&to&currency
GET    /api/v1/analytics/by-category   ?from&to&currency
GET    /api/v1/analytics/time-series   ?from&to&bucket=day|week|month&categoryId&currency
```

Errors are RFC 9457 Problem Details with a stable `code` field — branch on the
code, never on the English prose.

## Not built yet

Groups, splits, settlements, recurring payments and the autopay scheduler. Their
tables exist in `V1__initial_schema.sql`; the modules land in phases 3–5.
