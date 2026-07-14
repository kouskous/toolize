# Toolize

Import an OpenAPI specification and instantly expose the API as MCP tools —
no code, no restart.

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
- **Persistence**: single `projects.json` file under `/data`, rebuilt into the
  in-memory `ConcurrentHashMap` tool registry on startup — no database, per spec.
