# AGENTS.md

Guidance for AI agents (and humans) working in this repo. This is the real file; `CLAUDE.md` just
imports it (see [ADR-0005](docs/adr/0005-agents-md-as-the-single-source.md)).

## Project Overview

A headless **MiraVelo bike-leasing** blueprint: a BPMN process running on an embedded CIB seven
engine, behind an enforced hexagonal architecture. There is **no frontend** — the service is driven
over REST, and the CIB seven webapps (Cockpit / Tasklist) handle any human-in-the-loop steps.

- **Backend** (`service/app`) — Java 21 / Spring Boot 4, hexagonal, CIB seven 2.2 embedded engine
  (JavaDelegates invoked by expression, **not** Zeebe workers). Package root `io.miragon.blueprint`.
- **The contract** is `openapi/openapi.json`: springdoc generates it from the controllers, it is
  **committed and drift-gated** in CI. It is the published contract for any REST consumer — a backend
  REST change that isn't re-exported fails the drift gate. See
  [ADR-0003](docs/adr/0003-openapi-as-the-checked-in-contract.md).

## Repository Map

```
pom.xml                        root of the multi-module Maven build (modules under service/)
service/
  common-architecture-tests/   reusable ArchUnit rule suite (src/main/java)
  app/                         the CIB seven bike-leasing service (hexagonal)
    adapter/inbound/rest        domain REST controllers + OpenAPI / problem-details config
    adapter/inbound/cibseven    JavaDelegates + listeners for the BPMN service tasks
    adapter/outbound/cibseven   drives the engine (RuntimeService / TaskService) + task inbox
    adapter/outbound/db         JPA persistence (leasing applications + bike portfolio)
    adapter/outbound/…          simulated dealer / contract / insurance / notification adapters
    adapter/process             generated *ProcessApi (bpmn-to-code) + engine config
    application/{port,service}  use-case ports and their services
    domain/{leasing,bike}       pure domain model
    resources/{bpmn,dmn,forms}  the process models and Camunda Forms
    resources/db/migration      Flyway versioned schema migrations
bruno/                         REST scenarios (happy-path / escalation / abort / not-solvent / …)
openapi/                       the checked-in, drift-gated OpenAPI contract (openapi.json)
docs/                          Architecture Decision Records + diagrams
stack/                         Postgres dev stack (docker compose)
.github/                       pre-merge + nightly pipelines + Dependabot
.githooks/                     pre-commit hook (bpmnlint on staged .bpmn)
package.json / .bpmnlintrc     root-level bpmnlint config + git-hook installer (npm run lint:bpmn)
```

## Development Setup

Two commands to a running service:

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres
mvn -pl service/app spring-boot:run                 # backend + engine on :8080
```

### Ports (one source of truth — keep README, this file and `.conductor/settings.toml` in sync)

| What | Port |
|---|---|
| Postgres | 5432 |
| Backend (REST + `/engine-rest`) | 8080 |
| CIB seven Cockpit / Tasklist / webapps | 8080/camunda (admin/admin) |
| OpenAPI spec · Swagger UI | 8080/v3/api-docs · 8080/swagger-ui.html |
| Actuator (health/liveness/readiness · prometheus) | 8080/actuator |

Under Conductor the ports are fixed and the workspace runs `nonconcurrent`
(see [ADR-0006](docs/adr/0006-fixed-ports-for-v1-portless-as-the-upgrade.md)).

## Build Commands

| Area | Command |
|---|---|
| Backend (arch + unit + process + model validation + spec export) | `mvn verify` |
| Mutation testing (gate 80) | `mvn -pl service/app test-compile org.pitest:pitest-maven:mutationCoverage` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `mvn -pl service/app generate-sources` |
| Regenerate + verify the OpenAPI contract | `mvn -pl service/app test -Dtest=OpenApiSpecExportTest` then `git diff --exit-code openapi/openapi.json` |
| API scenarios (running stack) | `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r` |
| BPMN lint | `npm run lint:bpmn` |
| Backend OCI image | `mvn -pl service/app spring-boot:build-image` (image `miravelo/cibseven-embedded-example`) — [ADR-0011](docs/adr/0011-build-and-deployment-approach.md), CONTRIBUTING "Run it in containers" |

## Architecture — the rules are machine-enforced

The backend's hexagonal rules live in `service/common-architecture-tests` (ArchUnit) and
**fail the build** (see [ADR-0007](docs/adr/0007-two-architecture-test-tools-archunit-and-konsist.md)).
Read `HexagonalArchitectureTest.java` and `NamingConventionArchitectureTest.java` before writing code.
The two source-shape rules ArchUnit can't see — one top-level type per file, no wildcard imports — are
enforced by the **maven-checkstyle-plugin** (`config/checkstyle/checkstyle.xml`), which also fails the
build. The hard rules:

- **One inbound port per controller.** `onlyFulfilOneUseCase` counts constructor params in
  `application.port.inbound` and fails at >1. An inbox listing + a completion are two controllers.
- **No new top-level `config` package.** The containment rule ignores only *direct* members of the
  root package, so `io.miragon.blueprint.config` would fail. Cross-cutting `@Configuration` (CORS,
  OpenAPI, error handling) goes in `adapter.inbound.rest` — the `Configuration` suffix is whitelisted
  there.
- **`adapter/process` is generated.** Never hand-edit `*ProcessApi.java`; edit the `.bpmn` and re-run
  `mvn -pl service/app generate-sources`.
- **Suffixes:** inbound port `UseCase|Query`; outbound `Port|Repository|Process`; service
  `Service|Configuration`; `adapter.inbound.rest` `Controller|Dto|Input|Mapper|Configuration`;
  `adapter.inbound.cibseven` `Delegate|Worker|Listener`; `adapter.outbound`
  `PersistenceAdapter|Adapter|Mapper|Entity|Repository`.
- **Spring Data types stop at the adapter.** Ports own their own `Filter`/`Page`/`Criteria` types.

## BPMN Quality Gates

- `bpmn-to-code` generates typed process constants from the models at build time; a custom model
  test requires every service task to use a delegate expression (`#{beanName}`).
- `bpmnlint` runs on staged `.bpmn` via `.githooks/pre-commit` (install: `npm run hooks:install`).

## Testing

TDD. Match the test style to the layer:

| Layer | Test style |
|---|---|
| domain | plain unit tests |
| application service | Mockito unit tests (mock the ports) |
| `adapter.inbound.rest` | `@WebMvcTest` + `@MockitoBean` |
| `adapter.outbound.db` | `@DataJpaTest` |
| process end-to-end | CIB seven process tests |

**Mutation testing gates PRs at 80** (`pitest-maven`): a test that executes without asserting
will fail CI. Coverage says a line ran; mutation says a test would have noticed. The PR gate runs
**diff-scoped** (only the classes the PR changed, still blocking); the **full-module** gate-80 sweep
runs nightly. See [ADR-0004](docs/adr/0004-mutation-testing-as-a-blocking-pr-gate.md).

## Verify After Each Task (targeted, not a full build)

- Backend service/controller: `mvn -pl service/app test -Dtest='*<Name>Test'`
- Architecture only: `mvn -pl service/app test -Dtest=ArchitectureTest`
- Contract changed: regenerate the spec, then `git diff --exit-code openapi/openapi.json`
- Process changed: `mvn -pl service/app generate-sources` then the `process.*` tests

## Working with GitHub

Use the `gh` CLI. Write everything (issues, PRs, commit messages) in **English**. Use
**Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`, `ci:`, `build:`).

## ADRs

Architecture decisions are recorded in `docs/adr/`. Read them to understand *why* the repo is shaped
this way before proposing structural changes.

## Personality

You are a knowledgeable colleague, not someone who passively takes orders. If something proposed
doesn't look right, suggest corrections, ask critical questions, and push back where needed.
Challenge ideas that could benefit from further improvement or iterative refinement rather than just
accepting them at face value.
