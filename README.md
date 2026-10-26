# AI Document Review Assistant

A Spring Boot app where users upload documents, **Claude** reads them in the background and extracts a summary and key fields, and a **human reviewer** approves or corrects the result before it's accepted.

Built to practise: secure document management, background processing, AI API integration, human review / approval workflows, role-based security and audit logging.

```
 Uploader                         Background worker                     Reviewer
    │  POST /api/documents            │                                    │
    ├──────────────► UPLOADED ────────┤ (after commit, @Async)             │
    │  202 Accepted                   ├─► PROCESSING                       │
    │                                 │     extract text (PDFBox)          │
    │                                 │     call Claude API ──► JSON       │
    │                                 ├─► AWAITING_REVIEW ─────────────────┤
    │                                 │                                    ├─► APPROVED (fields confirmed / corrected)
    │                                 └─► FAILED (retry possible)          └─► REJECTED (comment required)
```

Every step is written to an append-only **audit log** (who did what, when).

## Tech

Java 17 · Spring Boot 3.5 · Spring Security · Spring Data JPA · PostgreSQL · Anthropic Java SDK (Claude, structured outputs) · Apache PDFBox · Docker Compose · JUnit 5 / MockMvc

## Project layout

```
src/main/java/com/jasitha/docreview/
  ai/         AiExtractor interface, ClaudeAiExtractor (real), StubAiExtractor (free, offline)
  audit/      AuditEvent entity + service (append-only history)
  config/     SecurityConfig (roles), AppProperties
  document/   Document entity, DocumentService (workflow rules), DocumentProcessor (background job)
  storage/    FileStorageService (safe file storage), TextExtractor (PDF/text)
  web/        REST controller, DTOs, error handling
```

## Run it

### 1. Start PostgreSQL

With Docker Desktop running:

```bash
docker compose up -d db
```

### 2. Run the app (stub AI: free, no API key)

```bash
./mvnw spring-boot:run
```

On Windows PowerShell use `.\mvnw.cmd spring-boot:run`. No Maven install needed; the wrapper downloads it.

### 3. Run the tests (no database or API key needed, uses H2 + stub AI)

```bash
./mvnw test
```

### Or run everything in Docker

```bash
docker compose up --build
```

## Try the API

Demo users (local only, passwords set in `application.yml` / `.env`): `uploader`, `reviewer`, `admin`.

```bash
# Upload (as uploader)
curl -u uploader:uploader-pass -F "file=@invoice.pdf;type=application/pdf" http://localhost:8080/api/documents

# Check status (poll until AWAITING_REVIEW)
curl -u reviewer:reviewer-pass http://localhost:8080/api/documents/1

# Approve, optionally with corrected fields
curl -u reviewer:reviewer-pass -H "Content-Type: application/json" \
  -d '{"fields":[{"name":"total_amount","value":"USD 1,250.00","confidence":"high"}],"comment":"Checked"}' \
  http://localhost:8080/api/documents/1/approve

# Reject (comment required)
curl -u reviewer:reviewer-pass -H "Content-Type: application/json" \
  -d '{"comment":"Wrong supplier"}' http://localhost:8080/api/documents/1/reject

# Retry a FAILED document, and view the audit trail
curl -u reviewer:reviewer-pass -X POST http://localhost:8080/api/documents/1/retry
curl -u reviewer:reviewer-pass http://localhost:8080/api/documents/1/audit
```

| Endpoint | Who |
|---|---|
| `POST /api/documents` | UPLOADER, ADMIN |
| `GET /api/documents`, `GET /api/documents/{id}` | everyone (uploaders only see their own) |
| `POST /api/documents/{id}/approve` · `/reject` · `/retry` | REVIEWER, ADMIN (not on your own upload) |
| `GET /api/documents/{id}/audit` | REVIEWER, ADMIN |

## Switch on real Claude

1. Get an API key from https://console.anthropic.com and **set a monthly spend limit** there first.
2. Copy `.env.example` to `.env` (for Docker) or set environment variables (for `mvnw`):

   ```powershell
   $env:AI_MODE = "claude"
   $env:ANTHROPIC_API_KEY = "<your key>"
   ```

3. Restart the app and upload a document.

`AI_MODEL` defaults to `claude-opus-5-5` (most capable). For cheap experimenting, set `AI_MODEL=claude-haiku-5-5`. A short document costs a few cents on Opus and a fraction of a cent on Haiku.

**How the Claude call works** (`ai/ClaudeAiExtractor.java`):
- **Structured outputs:** the SDK turns the `ExtractionResult` record into a JSON schema, so the reply always has the right shape. No string parsing.
- **Prompt-injection guard:** the document goes inside `<document>` tags and the system prompt says to treat it as data, never as instructions.
- **Refusals and truncation** (`stop_reason` = `refusal` / `max_tokens`) are turned into clear FAILED reasons.
- **Server-side fallbacks** are enabled, so a request a safety classifier declines can be retried on another model automatically.
- The SDK already retries rate limits and server errors (2 retries by default).
- Long documents are **rejected, not silently truncated** (`app.ai.max-text-chars`).

## What's deliberately simple (your next steps)

1. **React / Next.js review screen:** upload page, document list with status badges, review page with editable fields and Approve / Reject buttons.
2. **JWT login:** replace HTTP Basic + demo users with Spring Security JWT (or OAuth2 with Keycloak).
3. **Real queue:** swap `@Async` events for RabbitMQ or Kafka so jobs survive restarts.
4. **S3 storage:** swap `FileStorageService` for AWS S3, deploy to AWS (EC2/ECS + RDS) with Terraform.
5. **Flyway migrations:** replace `ddl-auto: update`.
6. **Scanned PDFs:** send the PDF itself to Claude (document block) instead of extracted text.
7. **Testcontainers:** run integration tests against real PostgreSQL.



> **AI Document Review Assistant:** Spring Boot + PostgreSQL app that extracts data from uploaded documents with the Claude API (structured outputs), using background processing, role-based access control, a human approval workflow and an audit log.
