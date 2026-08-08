# ContractGuard

Clause-level risk analysis for legal contracts. Upload a PDF, get every clause
split out, classified **Safe / Moderate / Risky**, and rewritten in plain English.

Spring Boot 3 · Spring Data JPA · Spring Security (JWT) · MySQL · Angular 18 · Apache PDFBox

---

## Run it

### Prerequisites

- **JDK 21 (LTS)** — not newer. Spring Boot 3.2.5 supports Java 17–21; it will
  not start on Java 25/26 (Byte Buddy and Hibernate reject the newer class file
  version). If your default JDK is newer, install 21 alongside it:

  ```bash
  brew install openjdk@21
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  ```

  That export is per-terminal, so your system default is untouched.
- MySQL running locally
- Node 18+
- A free API key from [console.groq.com](https://console.groq.com)

### Backend

```bash
cd backend

export LLM_API_KEY=gsk_your_key_here
export DB_USER=root
export DB_PASSWORD=your_mysql_password
export JWT_SECRET=$(openssl rand -base64 32)

mvn spring-boot:run
```

The schema is created automatically on first run (`ddl-auto: update`).
API comes up on `http://localhost:8080`.

> IntelliJ IDEA bundles Maven, so `mvn` works from its terminal even if it isn't
> on your system PATH. To generate a project-local wrapper instead, run
> `mvn -N wrapper:wrapper` once inside `backend/` and use `./mvnw` after that.

### Frontend

```bash
cd frontend
npm install
npm start
```

Open `http://localhost:4200`.

### Tests

```bash
cd backend && mvn test
```

Tests run against in-memory H2, so MySQL does not need to be running.

---

## Accuracy

Classification is measured against `samples/GROUND_TRUTH.md`, a hand-labelled
set covering both sample contracts. Every prompt change is re-measured against
it rather than eyeballed.

Prompt v1 defined the risk tiers with adjectives and collapsed the middle tier
entirely — the predatory sample scored 7 risky / 0 moderate / 5 safe. v2
replaced the adjectives with a decision test (unfair in *kind* vs unfair in
*degree*), added an explicit calibration statement, and gave one worked example
per tier. The middle tier now fires.

---

## Architecture

```
Angular 18 (localhost:4200)
    │  fetch + Authorization: Bearer <jwt>
    ▼
JwtAuthenticationFilter ──► SecurityContext
    ▼
DocumentController          ← ownership taken from the token, never the request
    ▼
DocumentService             ← orchestration + @Transactional boundary
    ├─► PdfExtractionService      (PDFBox → plain text)
    ├─► ClauseSegmentationService (text → List<Clause> with char offsets)
    └─► ClauseAnalyzerService     (clause → risk level + plain summary)
              └─► LlmClient       (the only class that touches HTTP)
    ▼
Spring Data JPA / Hibernate
    ▼
MySQL
```

### Schema

```
users                    documents                     clauses
─────                    ─────────                     ───────
id           PK  ┌──────►id            PK  ┌──────────►id            PK
email     UNIQUE │       user_id       FK ─┘           document_id   FK
password_hash    │       original_filename             order_index
full_name        │       stored_path                   heading
created_at       │       status        ENUM            original_text
                 │       extracted_text LONGTEXT       start_offset
                 └───────overall_score                 end_offset
                         uploaded_at                   risk_level    ENUM
                         analyzed_at                   plain_summary
                                                       rationale

Indexes:  documents(user_id, uploaded_at)   clauses(document_id)   clauses(risk_level)
```

**Why `start_offset` / `end_offset`?** Character positions into
`documents.extracted_text`. Storing the position rather than a second copy of the
text is what allows a future split-screen view to scroll to and highlight the
exact span in the original PDF without re-running the analysis.

---

## Design decisions

**Modular monolith, not microservices.** One deployment, and no component that
needs to scale independently. Splitting this into services would add network
calls, distributed transaction handling and deployment overhead for no benefit
at this size. Package boundaries are kept clean so a component could be
extracted later if that changes.

**CSRF disabled.** CSRF attacks work by getting a browser to replay a cookie it
already holds. We authenticate with a JWT in an `Authorization` header, which a
cross-site form post cannot set. The protection doesn't apply, so it's off
deliberately — not skipped.

**`@ManyToOne(fetch = LAZY)` everywhere.** JPA defaults `@ManyToOne` to EAGER,
which quietly turns one query into many. `DocumentRepository.findWithClausesByIdAndUserId`
uses an explicit `JOIN FETCH` to load a document and its clauses in one query.

**`open-in-view: false`.** Spring Boot defaults this to true, which keeps the
Hibernate session open through view rendering and lets lazy loading happen
outside the service layer. Turning it off means N+1 problems surface as errors
during development instead of silently in production.

**DTOs, never entities, at the controller boundary.** Keeps the JSON contract
stable when entities change, and prevents Jackson serialising a lazy proxy or
recursing through a bidirectional relationship forever.

**Files stored under a random UUID.** Using the uploaded filename directly would
let someone upload `../../etc/passwd` and escape the storage directory.

**Ownership scoped in the query, not checked after.** Every lookup is
`findByIdAndUserId(...)`. Fetching by id and then comparing owners in Java is
one forgotten `if` away from letting any user read any document.

**Analysis failures degrade, they don't throw.** A clause the model can't
classify becomes `UNKNOWN`, carries the reason in its `rationale` column, and the
document still completes. Per-item isolation means one bad clause cannot cost
you the other thirty-nine. `LlmClient` retries 429s and 5xx with exponential
backoff first, and fails fast on 4xx where retrying cannot help.

---

## Roadmap

- [x] Implement `calculateOverallScore` (capped when any clause is risky)
- [ ] Fix segmentation: sub-clauses, ALL-CAPS headings, citation false positives
- [ ] OCR for scanned PDFs (currently rejected)
- [x] Rewrite the analyzer prompt with few-shot examples
- [ ] Extend the labelled evaluation set to ~50 clauses and measure precision/recall
- [ ] Switch to `@Async` + frontend polling
- [ ] Split-screen PDF view with offset-based highlighting
- [ ] Dockerise

---

## Limitations

Scanned PDFs with no text layer are rejected — OCR isn't implemented. The model
can be wrong in both directions, and a false "Safe" is worse than a false
"Risky". This is not legal advice.
