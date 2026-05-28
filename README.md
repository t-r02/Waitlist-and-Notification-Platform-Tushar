# Waitlist And Notification Platform

A take-home backend exercise. Three Spring Boot services that work together to capture signups from a public form, let an internal team triage them in batches, and send confirmation and status-update emails along the way. Includes a referral feature with a leaderboard.

The whole thing comes up with one command and runs on a laptop.

---

## Prerequisites

You only need Docker to *run* it. Java and Gradle are bundled (or only needed if you want to run the tests outside Docker).

| For | You need |
|---|---|
| Running the stack | **Docker Desktop** (or Docker Engine + Docker Compose v2). Tested on Docker Desktop 4.x. |
| Running the demo script | `curl` and `jq`. Both come preinstalled on macOS and most Linux; on Windows, `curl` ships with Windows 10+ and `jq` can be installed via `winget install jqlang.jq` or `scoop install jq`. |
| Running the Postman collection headless | Node.js 18+ and `npx newman` (optional — you can also just import the collection into Postman). |
| Running tests on the host (not in Docker) | JDK 21. The Gradle wrapper handles the rest. |

System: about **8 GB of free RAM** (Kafka alone wants ~1 GB), **~3 GB free disk** for the images, and these ports free on `localhost`: `8081`, `8082`, `8083`, `5433`, `9092`, `6379`, `1025`, `8025`.

Works on macOS, Linux, and Windows (Docker Desktop with WSL2). The helper scripts come in both bash and PowerShell flavours.

---

## Quick start

```bash
./run.sh        # macOS / Linux
.\run.ps1       # Windows
```

That builds the three service images and brings up the full stack — three Spring Boot services, Postgres, Kafka (in KRaft mode, no Zookeeper), Redis, and Mailpit (a local SMTP catcher). It waits until every service reports healthy. First run takes 5–10 minutes because Gradle has to download dependencies into the build image; subsequent runs are seconds.

Then run the end-to-end demo:

```bash
./demo.sh       # macOS / Linux
.\demo.ps1      # Windows
```

It signs up a new user, logs in as the seeded admin (`admin / admin123`), approves the signup, and waits for the two emails (welcome + status update) to land in Mailpit. Prints `OK — 2 emails received` on success.

Open `http://localhost:8025` to see the actual emails. Tear everything down with `docker compose down -v`.

---

## Architecture

```
                    ┌──────────────────────────────────┐
   POST signup ────▶│  ingestion-service     :8081     │
   GET leaderboard  │                                  │
                    │  • rate limit + honeypot         │
                    │  • dedup on normalized email     │
                    │  • write entry + outbox in 1 tx  │
                    │  • outbox poller → Kafka         │
                    │  • consumes status-changed for   │
                    │    referral point awards         │
                    └─────┬──────────────────────▲─────┘
                          │ publish              │ consume
                          ▼                      │
                    ┌────────────────────────────┴─────┐
                    │            Kafka (KRaft)          │
                    │                                   │
                    │   waitlist.signup            ┐    │
                    │   waitlist.signup.dlt        │ fan│
                    │   waitlist.status-changed    │ out│
                    │   waitlist.status-changed.dlt│    │
                    └─────┬────────────────────────┬───┘
                          │                        │
                          ▼                        ▼
                    ┌──────────────────┐    ┌────────────────────────┐
   PATCH entries ──▶│ admin-service    │    │ notification-service   │
   from admins      │ :8082            │    │ :8083                  │
                    │                  │    │                        │
                    │ • JWT auth        │    │ • signup → "welcome"   │
                    │ • state machine   │    │ • status → "update"    │
                    │ • audit log       │    │ • idempotent on        │
                    │ • bulk operations │    │   event id             │
                    │ • keeps local     │    │ • DLT logger           │
                    │   projection of   │    │                        │
                    │   signups         │    └────────────┬───────────┘
                    │ • outbox poller   │                 ▼
                    │   → Kafka         │           Mailpit :8025
                    └──────────────────┘           (local SMTP catcher)

   Storage
     Postgres :5433   one instance, db `waitlist`, three schemas
                      ingestion:    signups, referrals, points, fingerprint, outbox
                      admin:        signups (projection), users, audit log, outbox
                      notification: notification_log (deduped by event id)
     Redis    :6379   leaderboard sorted sets (only used by ingestion)
```

The pattern that ties everything together: every state-changing write goes to the producing service's `outbox` table inside the same database transaction as the entity write. A scheduled poller in each service publishes those rows to Kafka asynchronously. Kafka never sees an event that wasn't first durably committed to Postgres, and a Kafka outage doesn't break the API — events just queue up in the outbox and drain when the broker is back.

---

## Run the tests

```bash
./gradlew test          # macOS / Linux
.\gradlew.bat test      # Windows
```

About 190 tests across the three services. Unit tests for the services, controllers, mappers, validators, the JWT signing and length validation, the state machine matrix, the rate-limit interceptor with real Bucket4j buckets, and the dedup race-fallback under concurrent threads. One `@EmbeddedKafka` integration test in the notification service covers both processing-failure and deserialization-failure routing to the dead-letter topic.

Tests run against H2 in PostgreSQL compatibility mode with Flyway disabled — fast, but it means the SQL migrations themselves aren't exercised in CI. A Testcontainers Postgres test per service would close that gap and is on the "what I'd add next" list.

---

## Try the API

Three ways, pick whichever suits you.

### Postman

```bash
newman run postman/waitlist.postman_collection.json \
       -e postman/waitlist.postman_environment.json \
       --delay-request 800 --timeout-request 15000
```

Or import both files into the Postman app. 68 assertions across 36 requests — happy paths, validation errors, the bulk 207, rate-limit triggers, the works.

### curl

```bash
# Sign up (referralCode null or omitted = no referral)
curl -X POST http://localhost:8081/api/public/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","name":"Alice","referralCode":null}'

# Leaderboard (all-time or current ISO week)
curl http://localhost:8081/api/public/leaderboard?window=all
curl http://localhost:8081/api/public/leaderboard?window=week

# Admin login → JWT
TOKEN=$(curl -s -X POST http://localhost:8082/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

# List, filter, approve
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/admin/entries
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8082/api/admin/entries?status=PENDING"
curl -X PATCH -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8082/api/admin/entries/1?status=APPROVED"

# Bulk (returns 207 on partial failure)
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"ids":[1,2,3],"newStatus":"APPROVED"}' \
  http://localhost:8082/api/admin/entries/bulk
```

### demo script

`./demo.sh` / `.\demo.ps1` walks the full happy path for you. Easier than the curl above if you just want to see it work end to end.

---

## Database

One Postgres instance, one database (`waitlist`), three schemas owned one-per-service. Each schema has its own Flyway migrations. No service queries another's tables directly — cross-service data flow only happens via Kafka events.

This is logical isolation by code discipline, not by DB-level grants. Every service connects as the `postgres` superuser today. For production, the next tightening step is per-schema users with `GRANT USAGE` restricted appropriately.

---

## Design choices

### Kafka in KRaft mode, not RabbitMQ or Redis Streams

Kafka's log lets the admin projection and the notification service have independent consumer groups that can replay from offset 0 after a deploy gap or a bug fix. A queue (RabbitMQ) would delete on ack and need a fan-out exchange exercise to get the same behaviour. KRaft skips the Zookeeper container — one fewer thing in the compose file. Tradeoff: Kafka is heavier than Rabbit when you don't actually need the replay story, but the projection pattern is what sealed it.

### Transactional outbox, not direct publish

The signup row and its outbox row are written in one DB transaction; a poller then publishes outbox rows to Kafka and marks them sent. The DB commit is the source of truth — either both land or neither does. Direct publish (write to DB, then call Kafka) risks the broker rolling back the commit on the DB side or the DB rolling back after Kafka already accepted the message. The cost is ~500 ms of publish latency. Worth it for the consistency.

### At-least-once delivery, with idempotency on `eventId`

Producers use `acks=all` and idempotent retries. Consumers don't auto-commit offsets — only after a record successfully processes. Every event carries a UUID set by the publisher; the notification and referral consumers reject duplicates via a `UNIQUE` constraint on that UUID in their respective dedup tables. Exactly-once would need Kafka transactions, which complicate the outbox shape. The cost of at-least-once is the occasional redundant DB read, which is fine.

### Dead-letter topic with bounded retries

When a consumer throws, the framework retries with exponential backoff (1s → 2s → 4s, capped at 10s) up to three times, then publishes the record to `<topic>.dlt` with the original headers and stack trace, and commits past it. Poison-pill exceptions (malformed JSON, validation errors) skip retries entirely and go straight to the DLT. Both value and key deserializers are wrapped with `ErrorHandlingDeserializer` so a record that fails to deserialize routes to the DLT too — otherwise it would crash-loop the consumer on the same offset forever.

### State machine in admin-service only

Ingestion is append-only. Admin owns the legal transitions: `PENDING → APPROVED → INVITED` (or `REJECTED → PENDING`). An illegal transition throws and the handler returns 409. This avoids two services disagreeing on what's legal. Ingestion learns about status changes by consuming `waitlist.status-changed` — which is also how it credits referral points.

### Referral points awarded on APPROVED, not signup

Ten points per referee, credited when admin approves the referee; reversed if subsequently rejected. Awarding at signup would incentivize invite-spam. Tying it to admin approval makes the leaderboard reflect real conversions. Tradeoff: referrers don't see their score move until admin acts, which is fine for a waitlist but wrong for a SaaS activation funnel.

### Per-IP rate limit and honeypot, not CAPTCHA

10 requests/IP/min plus a global 1000/min ceiling. The signup DTO has a `website` field — bots fill every field, real users leave it blank. A populated honeypot returns a convincing fake success and logs a WARN, so bots can't tell they were filtered. No CAPTCHA because the friction cost on real users isn't worth it for a closed waitlist. The IP comes from `X-Forwarded-For`; trust-allowlisting that header is on the "what I'd add" list.

### Schemas, not separate databases

One Postgres instance, three schemas, one schema per service. Same trade as monorepo vs polyrepo: easier ops, weaker isolation. Code discipline (no cross-schema queries) and per-service Flyway migrations enforce the boundary. The next tightening step is per-schema DB users with `GRANT USAGE` restricted to the owning schema; after that, separate databases inside the same instance, then separate instances.

---

## What I'd add next

- **OpenAPI spec** — `springdoc-openapi` on the controllers, so Swagger UI replaces most of the curl reference above.
- **Distributed tracing** — `X-Correlation-Id` is already on every request via MDC; OpenTelemetry would extend it across Kafka hops.
- **Proper RBAC for admin** — any valid JWT can do anything today. A role claim and method-level `@PreAuthorize` is an afternoon.
- **Debezium CDC** instead of the polling outbox — tails the Postgres WAL, removes the 500ms publish latency, deletes the scheduled thread.
- **Trust-proxy allowlist for `X-Forwarded-For`** — currently honored unconditionally, so the per-IP limit can be bypassed by rotating the header. Should be configurable.
- **Structured failures in `BulkStatusResponse`** — currently a list of strings; a list of `{id, code, message}` would be friendlier for clients.
- **Move the Kafka send outside the outbox transaction** — the publisher does a synchronous send inside `@Transactional`, holding DB row locks for up to two minutes on a stalled broker. Either shorten the broker timeout or split into two small transactions.
- **Testcontainers integration test per service** — closes the gap that H2-in-PG-mode doesn't cover the actual Flyway migrations.
