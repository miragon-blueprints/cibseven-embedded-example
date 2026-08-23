# CIB seven Bike-Leasing Blueprint

> [!NOTE]
> **🚧 Work in progress.** This is a **solution template** — a reference to fork and build on, for
> our consultants and anyone else — not a product that ships. It's still being fleshed out, so parts
> may be incomplete and it may not yet fully demonstrate what it's meant to. Treat it as a
> living example, and expect it to keep evolving.

A ready-to-fork **starting point** for automating a business process on
[CIB seven](https://cibseven.org) (the community fork of Camunda 7) with an **embedded engine**,
Spring Boot and Java — one complete, runnable, production-shaped BPMN service you can clone and make
your own.

## The scenario

Meet **MiraVelo** — a (fictional) lifestyle bike brand for the quarter-life-crisis crowd: gravel bikes
for the weekends that count, road bikes for everyone who just wants to feel the asphalt. MiraVelo sells
its bikes on a **leasing model** for private and corporate customers, and this project automates that
leasing application from the first request to an active lease.

It's a made-up company, so nobody gets hurt when the DMN politely declines a 15-year-old's application
for a carbon road bike.

## What's inside

Most engine examples stop at a happy-path service task. This one deliberately walks through the **broad
palette of BPMN elements you actually meet in real processes** — and the engineering scaffolding around
them — so a new project starts from something complete instead of a blank page:

![The bike-leasing process](docs/assets/bike-leasing.png)

- a **message start event**, **service tasks** (JavaDelegates) and a **DMN business-rule task**;
- an **embedded sub-process** with an **event-based gateway** (sign vs. a 14-day deadline) and a
  non-interrupting **7-day reminder timer**;
- a **parallel fork/join**, and a **user task with a Camunda Form** — completable in the Tasklist *or*
  via a REST endpoint;
- an **execution listener** on a service task and a **task listener** on the user task — the two
  common listener hooks, wired as Spring beans just like the delegates;
- **compensation / SAGA** handlers guarded by **error** and **escalation** boundary events;
- a **call activity** into a second process, a **message event sub-process** (application withdrawal),
  and a **terminate end event**.

## How it's built

```
service/
  common-architecture-tests/   reusable ArchUnit rule suite (src/main/java)
  app/                         the CIB seven bike-leasing service (hexagonal)
    adapter/inbound/rest        domain REST controllers + OpenAPI / problem-details config
    adapter/inbound/cibseven    JavaDelegates for the BPMN service tasks
    adapter/outbound/cibseven   drives the engine (RuntimeService / TaskService) + task inbox
    adapter/outbound/db         JPA persistence (leasing applications + bike portfolio)
    adapter/outbound/dealer     simulated bike dealer (stock check + order)
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
pom.xml                        root of the multi-module Maven build (modules under service/)
package.json                   root-level bpmnlint config + git-hook installer
```

- **Stack:** Java 21 · Spring Boot 4 · CIB seven 2.2 (embedded) · PostgreSQL · Flyway · a multi-module
  Maven build (root `pom.xml`, modules `service/common-architecture-tests` and `service/app`).
- **Generated process API:** the [`bpmn-to-code`](https://github.com/emaarco/bpmn-to-code) Maven
  plugin turns each `.bpmn` into a typed `*ProcessApi` class, so element ids, messages, timers and
  variables are compile-checked constants used by both delegates and tests.
- **Forms:** Camunda Forms (`.form`) are deployed with the process and render in the CIB seven
  Tasklist/Cockpit for the user tasks.
- **OpenAPI contract:** `springdoc` serves the live spec at `/v3/api-docs` (Swagger UI at
  `/swagger-ui.html`); a test exports it to [`openapi/openapi.json`](openapi/openapi.json) and CI fails
  on drift, so the committed contract can never lie about the code (see ADR-0003).
- **BPMN linting:** [`bpmnlint`](https://github.com/bpmn-io/bpmnlint) — `bpmnlint:recommended`,
  `camunda-compat` and the central [`@miragon/bpmnlint-plugin-rules`](https://www.npmjs.com/package/@miragon/bpmnlint-plugin-rules)
  — gates the `.bpmn` models (`--max-warnings=0`), run in CI and as a pre-commit hook.

## Design decisions

- **Hexagonal architecture** keeps the engine and framework at the edges: the domain and use cases
  never depend on CIB seven, so business logic is testable and the engine is replaceable. The
  `service/common-architecture-tests` module enforces this with **ArchUnit** (bytecode: layering,
  dependency direction, naming), with **Checkstyle** covering the two source rules (one top-level type
  per file, no wildcard imports) — one line wires it into a service:
  `class ArchitectureTest extends ServiceArchitectureTest`.
- **Unit tests** (JUnit 5 + Mockito) cover every domain type, application service and adapter with
  given/when/then comments and shared `testLeasingApplication(...)` builders — controllers via
  `@WebMvcTest`, persistence via `@DataJpaTest`. JavaDelegates are covered by the process tests.
- **Process tests** (`cibseven-bpm-assert`) drive the deployed model deterministically — timers and
  async continuations are fired and messages correlated by hand — covering happy-path, escalation,
  abort, DMN rejection, and the bike-unavailable → alternative-selection loop.
- **Model validation** (`bpmn-to-code-testing`) checks the `.bpmn` models structurally at build time
  (`BpmnRules.all()` plus a custom rule requiring every service task to use a delegate expression).
- **Mutation testing** (PIT, gate 80) grades assertion strength, not just line coverage — diff-scoped
  on every PR and a full sweep nightly (ADR-0004).
- **Bruno + CI** proves the same scenarios against the *running* app: domain REST endpoints drive the
  business actions, and the CIB seven `/engine-rest` API completes user tasks and fires timer jobs so
  the whole flow runs in the pipeline without real 14-day waits.
- **Ops-ready out of the box:** Flyway versioned migrations with Hibernate on `validate` (ADR-0010),
  actuator health/liveness/readiness probes + Prometheus metrics (ADR-0009), and an OCI image built by
  `mvn -pl service/app spring-boot:build-image` — no Dockerfile (ADR-0011).
- **Dependabot** keeps Maven, the Postgres image, the BPMN tooling and GitHub Actions current.

The *why* behind each of these choices is recorded as an [Architecture Decision Record](docs/README.md).

## Run it

```bash
# 1. start Postgres
docker compose -f stack/docker-compose.yml up -d

# 2. run the app (CIB seven Cockpit/Tasklist at http://localhost:8080/camunda, admin/admin;
#    Swagger UI at http://localhost:8080/swagger-ui.html)
mvn -pl service/app spring-boot:run

# 3. lint the BPMN models
npm ci && npm run lint:bpmn

# 4. drive the scenarios (build + arch + process tests first, then the REST flows)
mvn verify
cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r
```

Start a case with `POST http://localhost:8080/api/bike-leasing`
(`{ "customerName": …, "email": …, "age": 35, "monthlyNetIncome": 3500, "bikeId": "BIKE-900", "bikeModel": "Gravel Explorer 900" }`).

The `age` and `monthlyNetIncome` feed the `checkCreditRating` DMN; the `bikeId` identifies the bike and
is the *only* bike attribute the engine ever carries. The descriptive `bikeModel` lives in a separate
**bike portfolio** aggregate (its own `bike_portfolio` table, keyed by `bikeId`) — never as a process
variable — and `GET /api/bike-leasing/{id}` resolves it back from there.

If the requested bike is out of stock, the `Clarify alternative with customer` user task can be resolved
**two ways**, a deliberate contrast:

- the **recommended** path — a client calls `POST …/api/bike-leasing/{id}/clarify-alternative`, which
  routes through the domain (persisting the chosen alternative) *before* completing the task; versus
- the **form-only** path on `clarify-return` in `cancel-bike-order.bpmn`, kept as a counter-example:
  completing it via the Camunda Form or `/engine-rest` never touches the domain, so its data lands only
  in process variables (see the `bpmn:documentation` on each task).

Bike availability itself is decided by a `BikeDealerPort` outbound adapter (`checkAvailability` /
`order`) whose small out-of-stock deny-list drives the branch.

## Incident demo

Want to teach **transaction boundaries, retries and incidents**? Submit a request for the poison bike
`BIKE-FAIL`: the simulated dealer "outage" fails the *Order bike from dealer* job, its retries count
down (`R3/PT10S`), and an **incident** appears in the Cockpit to analyze and retry. A ready-to-run
Bruno collection lives in `bruno/06-incident-demo/`.

## Contributing

Contributions are welcome. Please open an issue to discuss substantial changes first, keep the
architecture tests green (`mvn verify`), and use
[Conventional Commits](https://www.conventionalcommits.org) for commit messages and PR titles.

## License

Licensed under the [MIT License](./LICENSE).
