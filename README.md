# Toolize

Import an OpenAPI specification and instantly expose the API as MCP tools —
no code, no restart.

🌐 [Landing page](https://kouskous.github.io/toolize/)

## Quick start (Docker)

```bash
docker build -t toolize .
docker run -p 8080:8080 -v toolize-data:/data toolize
```

Then open:

```
http://localhost:8080
```

Import an OpenAPI/Swagger URL or upload a YAML/JSON file, and the generated
MCP tools are immediately available at:

```
http://localhost:8080/mcp
```

Point Claude Desktop, Cursor, or any MCP client (that supports the
Streamable HTTP transport) at that URL.

## Connecting a production database

Toolize stores everything in an embedded H2 database by default (see the quick
start above) — nothing to configure. For production, switch to a real
PostgreSQL, MySQL, or Oracle instance with `TOOLIZE_DB_*` environment
variables; no image rebuild needed.

**PostgreSQL**

```bash
docker run -p 8080:8080 -v toolize-data:/data \
  -e TOOLIZE_DB_TYPE=POSTGRESQL \
  -e TOOLIZE_DB_HOST=db.example.com \
  -e TOOLIZE_DB_PORT=5432 \
  -e TOOLIZE_DB_NAME=toolize \
  -e TOOLIZE_DB_USERNAME=toolize \
  -e TOOLIZE_DB_PASSWORD=change-me \
  toolize
```

**MySQL**

```bash
docker run -p 8080:8080 -v toolize-data:/data \
  -e TOOLIZE_DB_TYPE=MYSQL \
  -e TOOLIZE_DB_HOST=db.example.com \
  -e TOOLIZE_DB_PORT=3306 \
  -e TOOLIZE_DB_NAME=toolize \
  -e TOOLIZE_DB_USERNAME=toolize \
  -e TOOLIZE_DB_PASSWORD=change-me \
  toolize
```

**Oracle**

```bash
docker run -p 8080:8080 -v toolize-data:/data \
  -e TOOLIZE_DB_TYPE=ORACLE \
  -e TOOLIZE_DB_HOST=db.example.com \
  -e TOOLIZE_DB_PORT=1521 \
  -e TOOLIZE_DB_NAME=orclpdb1 \
  -e TOOLIZE_DB_USERNAME=toolize \
  -e TOOLIZE_DB_PASSWORD=change-me \
  toolize
```

Notes:

- `TOOLIZE_DB_PORT` is optional — it defaults to that database's standard port
  (5432 / 3306 / 1521) if omitted.
- If the database runs on your host machine rather than another container, use
  `host.docker.internal` instead of `db.example.com`.
- With `docker-compose`, `TOOLIZE_DB_HOST` is just the other service's name
  (e.g. `postgres`), and these variables belong under `environment:` — ideally
  sourced from a secret rather than hardcoded in the compose file.
- Because this is plain environment configuration (not a config file on local
  disk), every replica of a horizontally scaled deployment — e.g. a Kubernetes
  Deployment with these values sourced from a Secret — connects to the same
  database identically, regardless of what any one pod's local disk contains.

## Local development (without Docker)

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend (in a second terminal, proxies /api and /mcp to :8080):

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` for the dev UI.

## Project layout

```
toolize
├── backend    Spring Boot (WebFlux) + swagger-parser + dynamic MCP server
├── frontend   Vue 3 + Vite + TypeScript + Tailwind
└── Dockerfile multi-stage build producing a single toolize.jar
```

## Notes on this implementation

- **Java / Spring Boot versions**: the spec called for Java 24 / Spring Boot 4,
  which are not yet stable/available. This implementation uses **Java 21 (LTS)
  and Spring Boot 3.3** so the project actually builds and runs today. Bumping
  versions later is a one-line change in `pom.xml`.
- **MCP transport**: implemented as a minimal JSON-RPC 2.0 endpoint at `/mcp`
  supporting `initialize`, `tools/list`, and `tools/call` (Streamable HTTP,
  without SSE streaming). This avoids depending on the still-evolving Spring AI
  MCP Server starter, while remaining compatible with MCP clients that speak
  HTTP JSON-RPC.
- **Persistence**: JPA/Hibernate, defaulting to an embedded file-based H2 database
  under `/data` (zero setup - `docker run` just works), with the schema
  auto-created/updated at startup. The running tool registry is still an in-memory
  `ConcurrentHashMap`, rebuilt from the database on every startup.
- **Production database**: `TOOLIZE_DB_TYPE` and friends switch off the embedded
  H2 database onto a real PostgreSQL, MySQL, or Oracle instance with no code or
  image rebuild needed, and no dependency on Spring's own property names - see
  "Connecting a production database" above for the full list of variables and
  an example per database.
