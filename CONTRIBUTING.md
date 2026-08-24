# Contributing

Thanks for your interest in the CIB seven embedded bike-leasing blueprint! Contributions of all kinds
are welcome — bug reports, feature ideas, docs, and code.

## Getting started

```bash
git clone git@github.com:miragon-blueprints/cibseven-embedded-example.git
cd cibseven-embedded-example
npm ci && npm run hooks:install   # BPMN lint + git hooks
```

You need **JDK 21** and **Docker (or Podman)** for Postgres. BPMN linting uses Node (the root
`package.json`) but the service itself is a pure Maven/Java build with no Node runtime dependency.

Run the service locally:

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres
mvn -pl service/app spring-boot:run                 # backend + engine on :8080
```

### Ports

| What | Port |
|---|---|
| Postgres | 5433 (host) → 5432 (container) |
| Backend (REST + `/engine-rest`) | 8080 |
| CIB seven Cockpit / Tasklist / webapps | 8080/camunda (admin/admin) |
| OpenAPI spec · Swagger UI | 8080/v3/api-docs · 8080/swagger-ui.html |
| Actuator (health · liveness/readiness · prometheus) | 8080/actuator |

Under Conductor the ports are fixed and the workspace runs `nonconcurrent` (see
[ADR-0006](docs/adr/0006-fixed-ports-for-v1-portless-as-the-upgrade.md)).

### Smoke test

With the service running, drive the REST scenarios end to end:

```bash
cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r
```

The happy path submits an application, signs the contract, and reports the handover; the other numbered
folders cover escalation, abort, not-solvent, bike-unavailable, the incident demo, and the list/inbox
endpoints. Confirm <http://localhost:8080/camunda> (admin/admin), <http://localhost:8080/swagger-ui.html>
and <http://localhost:8080/actuator/health> (status `UP`) all load.

## Run it in containers

The dev loop above runs the backend from source. To run it as a container instead, build the backend
OCI image (Spring buildpacks — no Dockerfile) and run it against the Postgres compose stack. The
rationale is in [ADR-0011](docs/adr/0011-build-and-deployment-approach.md).

```bash
# 1. build the backend OCI image. Produces miravelo/cibseven-embedded-example:1.0-SNAPSHOT
mvn -pl service/app spring-boot:build-image

# 2. start Postgres
docker compose -f stack/docker-compose.yml up -d

# 3. run the image against it (host networking; point it at the compose Postgres)
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/bikeleasing \
  -e SPRING_DATASOURCE_USERNAME=admin -e SPRING_DATASOURCE_PASSWORD=admin \
  miravelo/cibseven-embedded-example:1.0-SNAPSHOT
```

Then open <http://localhost:8080/camunda> (admin/admin), <http://localhost:8080/swagger-ui.html> and
<http://localhost:8080/actuator/health>.

**Podman:** `spring-boot:build-image` needs a Docker-API socket. Expose podman's and point the build at it:

```bash
podman system service --time=0 unix:///tmp/podman.sock &
export DOCKER_HOST=unix:///tmp/podman.sock
mvn -pl service/app spring-boot:build-image
```

**Configuration.** `application.yaml` ships dev defaults; the deploy-relevant values are read from the
environment (they win over the baked defaults):

| Env var | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://localhost:5433/bikeleasing` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | DB credentials | `admin` / `admin` |

> **Not production-hardened.** The image carries the example `jwtSecret` and admin/admin credentials
> from `application.yaml`. Override them (and the DB credentials) before running anywhere real. Schema
> is owned by Flyway and Hibernate only validates ([ADR-0010](docs/adr/0010-flyway-for-database-migrations.md)),
> so the Postgres volume persists across `down`/`up` — reset it with `docker compose -f
> stack/docker-compose.yml down -v`.

## Scripts

```bash
# backend
mvn verify                              # arch + unit + process + model validation + spec export
mvn -pl service/app test-compile org.pitest:pitest-maven:mutationCoverage   # mutation score >= 80
mvn -pl service/app generate-sources    # regenerate the typed process API after editing a .bpmn

# BPMN
npm run lint:bpmn        # bpmnlint the .bpmn models
```

## Ground rules

- **Start from an issue.** Every change traces back to one — open an issue (or pick an existing one)
  and agree on the approach *before* you write code, then reference it in the PR (`Closes #123`).
  This keeps substantial changes discussed up front and the history navigable.
- **Read [`AGENTS.md`](AGENTS.md) first.** It is the single source of guidance for humans and AI
  agents alike (see [ADR-0005](docs/adr/0005-agents-md-as-the-single-source.md)).
- **Conventional Commits.** Commit messages and PR titles follow
  [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`,
  `refactor:`, `test:`, `chore:`). Write everything in **English**.
- **Keep the gates green.** The architecture (ArchUnit + Checkstyle), contract-drift and mutation (≥ 80)
  gates run in CI on every PR. They are fitness functions, not style guides — a violation fails the
  build. The mutation gate is **diff-scoped** on PRs (only the classes you changed); the full-module
  gate-80 sweep runs nightly.
- **Add tests.** This is a TDD codebase; match the test style to the layer (see `AGENTS.md`).
  Mutation testing means a test that runs without asserting will fail CI.
- **Changing the API?** Re-export the spec (the `OpenApiSpecExportTest`, which `mvn verify` runs)
  so the committed `openapi/openapi.json` contract stays in sync — it is **drift-gated in CI**.
- **Changing the process?** Edit the `.bpmn` model, re-run `mvn -pl service/app generate-sources` so the
  typed `*ProcessApi` stays in sync, and lint it with `npm run lint:bpmn`.
- **Changing the database schema?** Flyway owns it. Add a new forward-only migration
  `V{n}__description.sql` under `service/app/src/main/resources/db/migration/` in the same change as
  the entity edit — never edit an already-applied migration. Hibernate runs `validate`, so a mismatch
  fails startup. A dev database first created by the old `ddl-auto: create` has no Flyway history;
  reset it once with `docker compose -f stack/docker-compose.yml down -v` before running. See
  [ADR-0010](docs/adr/0010-flyway-for-database-migrations.md).

## Before opening a PR

```bash
mvn verify
git diff --exit-code openapi/openapi.json    # the API contract must not drift
mvn -pl service/app test-compile org.pitest:pitest-maven:mutationCoverage   # mutation score >= 80
```

All of these run in CI on every pull request (JDK 21). Before opening a PR, sanity-check that a feature
is wired end to end across the backend — BPMN element → outbound port → service → inbound port → REST
controller → `openapi.json` → Bruno scenario.

## Reporting bugs / requesting features

Open an issue. For a process- or contract-related bug, attaching the relevant `.bpmn` model or the
`openapi.json` diff is the fastest path to a fix.
