# Price Intelligence Platform

Starter project for the AI Product & Competitor Price Intelligence Platform.
Stack: **Java 21 + Spring Boot 4 + PostgreSQL 17 + React (Vite)**.

## What's installed on this machine

| Tool | Version | Notes |
|------|---------|-------|
| Java (Temurin JDK) | 21 LTS | `java -version` |
| Maven | 3.9.9 | in `C:\Tools\apache-maven-3.9.9` |
| Node.js / npm | 24 / 11 | `node -v` |
| PostgreSQL | 17 | Windows service `postgresql-x64-17` (auto-starts) |

## Database credentials

| What | Value |
|------|-------|
| Host / Port | localhost : 5432 |
| Superuser (admin) | `postgres` / password `postgres` |
| App database | `price_intel` |
| App user | `price_admin` / password `price_admin_pass` |

> These are local development passwords. Change them before any real deployment.

## How to run

Open **two** terminals.

### 1. Backend (Spring Boot API) — http://localhost:8080
```powershell
cd C:\Users\Tapas\price-intelligence-platform\backend
mvn spring-boot:run
```
First run downloads dependencies. API base: `http://localhost:8080/api/v1/products`

### 2. Frontend (React) — http://localhost:5173
```powershell
cd C:\Users\Tapas\price-intelligence-platform\frontend
npm run dev
```
Open http://localhost:5173 in a browser.

## Project layout
```
price-intelligence-platform/
├─ backend/                       Spring Boot (Java)
│  └─ src/main/java/com/priceintel/backend/
│     ├─ BackendApplication.java  app entry point
│     ├─ Product.java             database entity (table: product)
│     ├─ ProductRepository.java   database access (CRUD, auto-generated)
│     └─ ProductController.java   REST endpoints (/api/v1/products)
│  └─ src/main/resources/application.properties   DB config
└─ frontend/                      React + Vite
   └─ src/App.jsx                 UI: list + add products
```

## Useful commands
- Connect to the DB:
  `& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U price_admin -h localhost -d price_intel`
- Build backend jar: `mvn clean package` (output in `backend/target/`)

## Next steps (from the FRD)
1. Expand the data model (product_identifier, competitor_listing, price_snapshot, ...).
2. Add marketplace adapters (Amazon SP-API, eBay Browse API).
3. Add the AI Gateway (OpenAI / Claude) for extraction & matching.
4. Build search orchestration, profit calculations, and the dashboard UI.
