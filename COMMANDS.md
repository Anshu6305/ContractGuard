# ContractGuard — command reference

Day-to-day commands. `run.sh` sets `JAVA_HOME` and loads `.env`, so neither
needs exporting by hand.

---

## Daily startup

Two terminal windows.

**Terminal 1 — backend**

```bash
cd ~/Documents/ContractGuard
./run.sh mysql
```

Wait for `Started ContractGuardApplication`. Runs on `http://localhost:8080`.

**Terminal 2 — frontend**

```bash
cd ~/Documents/ContractGuard/frontend
npm start
```

Wait for `Local: http://localhost:4200/`. Open that in your browser.

Stop either with **Ctrl+C**.

---

## run.sh modes

| Command | What it does |
|---|---|
| `./run.sh mysql` | Backend against MySQL — your normal mode |
| `./run.sh` | Backend against embedded H2 — no MySQL needed, useful on another machine |
| `./run.sh test` | Runs all JUnit + Mockito tests (H2, no MySQL) |
| `./run.sh build` | Builds the jar into `backend/target/` |

Capture output to a file when something breaks:

```bash
./run.sh mysql 2>&1 | tee backend.log
```

---

## MySQL

```bash
brew services start mysql     # start
brew services stop mysql      # stop
brew services list            # is it running?
mysql -u root -p              # connect (password is in .env as DB_PASSWORD)
```

Useful queries once connected:

```sql
USE contractguard;

SHOW TABLES;
DESCRIBE clauses;

-- the risk split, straight from SQL - faster than counting in the UI
SELECT risk_level, COUNT(*) AS n
FROM clauses
GROUP BY risk_level
ORDER BY n DESC;

-- clauses per document
SELECT d.original_filename, COUNT(c.id) AS clauses, d.overall_score
FROM documents d LEFT JOIN clauses c ON c.document_id = d.id
GROUP BY d.id;

-- confirm the text columns are TEXT, not varchar(255)
SHOW CREATE TABLE clauses;
```

Exit with `\q`.

> That `GROUP BY risk_level` query is how prompt changes get measured — run it
> before and after each edit and record the result in `samples/GROUND_TRUTH.md`.

---

## Reset the database

When you change an entity's column type — `ddl-auto: update` adds columns but
never alters existing ones, so a type change needs a fresh schema.

```bash
mysql -u root -p -e "DROP DATABASE contractguard;"
rm -f ~/Documents/ContractGuard/backend/uploads/*.pdf
```

Restart the backend and it rebuilds. You'll need to sign up again.

---

## Git

```bash
cd ~/Documents/ContractGuard

git status                    # .env must NEVER appear here
git add .
git commit -m "Describe what changed"
git push
```

First time only:

```bash
git init
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/contractguard.git
git push -u origin main
```

---

## Java version

Spring Boot 3.2.5 needs **Java 17–21** and will not start on 25 or 26.
`run.sh` points at 21 automatically. Running Maven by hand needs:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
java -version    # must print 21.x
```

---

## Troubleshooting

**Port already in use**

```bash
lsof -ti:8080 | xargs kill -9     # backend
lsof -ti:4200 | xargs kill -9     # frontend
```

**Frontend won't build after switching machines**

```bash
cd ~/Documents/ContractGuard/frontend
rm -rf node_modules package-lock.json
npm install
```

**Check the backend is alive**

```bash
curl -i http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"x@x.com","password":"wrongpass"}'
```

A `401` is success here — it means the server is up and rejecting bad
credentials, which is exactly what it should do.

**Common errors**

| Error | Cause |
|---|---|
| `Access denied for user 'root'` | Wrong `DB_PASSWORD` in `.env` |
| `Communications link failure` | MySQL not running — `brew services start mysql` |
| `LLM_API_KEY is not set` | Missing or placeholder value in `.env` |
| `Data too long for column` | Entity needs `columnDefinition`, and the DB needs dropping |
| `UnsupportedClassVersionError` | Wrong Java — check `java -version` is 21 |

---

## Open work

See the roadmap in `README.md`. The next items, and where they live:

| Task | File |
|---|---|
| ALL-CAPS and Roman-numeral headings | `ClauseSegmentationService.java` |
| OCR for scanned PDFs | `PdfExtractionService.java` |
| Extend the labelled eval set to ~50 clauses | `samples/GROUND_TRUTH.md` |
| Move analysis off the request thread | `DocumentService.analyzeAsync` |
