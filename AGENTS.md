# AGENTS.md

Guidance for AI agents (and humans) working in this repo. This is the real file; `CLAUDE.md` just
imports it (see [ADR-0005](docs/adr/0005-agents-md-as-the-single-source.md)).

## Project Overview

A headless **MiraVelo bike-leasing** blueprint: a BPMN process running on an embedded CIB seven
engine, behind an enforced hexagonal architecture. There is **no frontend** — the service is driven
over REST, and the CIB seven webapps (Cockpit / Tasklist) handle any human-in-the-loop steps.

- **Backend** — Spring Boot 4, hexagonal, CIB seven 2.2 embedded engine (JavaDelegates invoked by
  expression, **not** Zeebe workers). Package root `io.miragon.blueprint`.
  <!-- variant:blueprint -->
- **Two equivalent variants on `main`** — `kotlin-gradle/` (Kotlin, **the recommended stack**) and
  `java-maven/` (Java 21, for teams bound to it and for trainings). Each is self-contained and
  carries its own copy of the process models, forms, migrations and `application.yaml`; the
  `Blueprint Checks` workflow fails when the two `src/main/resources` trees differ, so **edit a model in
  one variant and copy it to the other**. The variants must stay functionally identical: **make every change in behaviour in both variants in the same
  PR.** Changes that only concern one language's idioms stay on that side. See
  [ADR-0013](docs/adr/0013-two-stack-variants-side-by-side-on-main.md). The OpenAPI contract below is
  drift-gated against **both** variants.
  Stack-specific content in shared files (docs, Dependabot, Conductor settings) is wrapped in
  `variant:<name>` markers so `scripts/create-starter.sh` can strip it — see
  [docs/starter.md](docs/starter.md).
  <!-- /variant:blueprint -->
- **The contract** is `openapi/openapi.json`: springdoc generates it from the controllers, it is
  **committed and drift-gated** in CI. It is the published contract for any
  REST consumer — a backend REST change that isn't re-exported fails the drift gate. See
  [ADR-0003](docs/adr/0003-openapi-as-the-checked-in-contract.md).

## Repository Map

<!-- variant:kotlin-gradle variant:nested -->
- `kotlin-gradle/` — the service in Kotlin, built with Gradle
  <!-- /variant:kotlin-gradle -->
  <!-- variant:java-maven variant:nested -->
- `java-maven/` — the service in Java 21, built with Maven
  <!-- /variant:java-maven -->

The service:

```
service/
  common-architecture-tests/   reusable architecture rule suite
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
```

Around it:

```
bruno/                         REST scenarios (happy-path / escalation / abort / not-solvent / …)
openapi/                       the checked-in, drift-gated OpenAPI contract (openapi.json)
docs/                          Architecture Decision Records + diagrams
stack/                         Postgres dev stack (docker compose)
.github/                       pre-merge + nightly pipelines + Dependabot
.githooks/                     pre-commit hook (bpmnlint on staged .bpmn)
.claude/                       Claude Code skills, review agent, guards on generated files, hook, plugins
.operate.json                  operate's engine profile: the local engine on :8081
scripts/create-starter.sh      strips the repo down to one variant (docs/starter.md)
package.json / .bpmnlintrc     root-level bpmnlint config, bpmn-cli, operate + git-hook installer
```

## Ground Rules for Changes

- **Ask before deciding business logic.** Gateway conditions, deadlines, error paths and who works a
  user task are business decisions: propose, don't pick.
- **Derive, never invent.** Details come from the issue, the model or the code; mark anything else
  as an assumption.
- **Keep element ids stable.** The generated process API and the process tests follow a rename
  through the compiler, but the Bruno scenarios name element ids as strings
  (`serviceTask_orderBike`, `userTask_clarifyAlternative`, …): a rename updates `bruno/` in the same
  change.

## Development Setup

Two commands to a running service — Postgres, then the backend + engine on :8081:

```bash
docker compose -f stack/docker-compose.yml up -d
```

<!-- variant:kotlin-gradle -->
```bash
cd kotlin-gradle && ./gradlew :service:app:bootRun
```
<!-- /variant:kotlin-gradle -->
<!-- variant:java-maven -->
```bash
cd java-maven && ./mvnw -DskipTests install && ./mvnw -pl service/app spring-boot:run
```
<!-- /variant:java-maven -->

### Ports (one source of truth — keep README, this file and `.conductor/settings.toml` in sync)

| What | Port |
|---|---|
| Postgres | 5432 |
| Backend (REST + `/engine-rest`) | 8081 |
| CIB seven Cockpit / Tasklist / Admin | 8081/camunda (admin/admin) |
| CIB seven webclient (new UI, cockpit entry point) | 8081/webapp/#/seven/auth/start (admin/admin) |
| OpenAPI spec · Swagger UI | 8081/v3/api-docs · 8081/swagger-ui.html |
| Actuator (health/liveness/readiness · prometheus) | 8081/actuator |

Under Conductor the ports are fixed and the workspace runs `nonconcurrent`
(see [ADR-0006](docs/adr/0006-fixed-ports-for-v1-portless-as-the-upgrade.md)).

## Build Commands

<!-- variant:kotlin-gradle -->
| Area (run in `kotlin-gradle/`) | Command |
|---|---|
| Backend (arch + unit + process + model validation + spec export) | `./gradlew build` |
| Mutation testing (gate 80) | `./gradlew :service:app:pitest` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `./gradlew generateBpmnModels` |
| Regenerate the OpenAPI contract | `./gradlew :service:app:test --tests "io.miragon.blueprint.adapter.inbound.rest.OpenApiSpecExportTest"` |
| Backend OCI image (`miravelo/cibseven-embedded-example`) | `./gradlew :service:app:bootBuildImage` |
<!-- /variant:kotlin-gradle -->

<!-- variant:java-maven -->
| Area (run in `java-maven/`) | Command |
|---|---|
| Backend (arch + unit + process + model validation + spec export) | `./mvnw verify` |
| Mutation testing (gate 80) | `./mvnw -pl service/app -am test-compile org.pitest:pitest-maven:mutationCoverage` |
| Regenerate the typed BPMN process API (after editing a `.bpmn`) | `./mvnw -pl service/app generate-sources` |
| Regenerate the OpenAPI contract | `./mvnw -pl service/app -am test -Dtest=OpenApiSpecExportTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Backend OCI image (`miravelo/cibseven-embedded-example`) | `./mvnw -pl service/app -am -DskipTests spring-boot:build-image` |
<!-- /variant:java-maven -->

| Area (repo root) | Command |
|---|---|
| Verify the OpenAPI contract after regenerating it | `git diff --exit-code openapi/openapi.json` |
| API scenarios (running stack) | `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r` |
| BPMN lint | `npm run lint:bpmn` |

The OCI image decision is [ADR-0011](docs/adr/0011-build-and-deployment-approach.md); the how-to is
CONTRIBUTING "Run it in containers".

## Architecture — the rules are machine-enforced

The backend's hexagonal rules live in `service/common-architecture-tests` (ArchUnit, plus a
source-level tool for the rules bytecode cannot express) and **fail the build** (see [ADR-0007](docs/adr/0007-two-architecture-test-tools-archunit-and-konsist.md)).
Read `HexagonalArchitectureTest.kt` and `NamingConventionArchitectureTest.kt` before writing code. The
hard rules:

- **One inbound port per controller and per delegate.** `onlyFulfilOneUseCase` counts constructor
  params in `application.port.inbound` and fails at >1. An inbox listing + a completion are two
  controllers.
- **No new top-level `config` package.** The containment rule ignores only *direct* members of the
  root package, so `io.miragon.blueprint.config` would fail. Cross-cutting `@Configuration` (CORS,
  OpenAPI, error handling) goes in `adapter.inbound.rest` — the `Configuration` suffix is whitelisted
  there.
- **`adapter/process` is generated.** Never hand-edit `*ProcessApi` or the shared
  `ServiceTasks`/`Messages`/`ProcessVariables`/`Errors`/`Escalations` files; edit the `.bpmn` and
  regenerate. The same holds for `openapi/openapi.json`. `.claude/settings.json` denies these edits,
  and CI fails when the committed files are stale.
- **Suffixes:** inbound port `UseCase|Query`; outbound `Port|Repository|Process`; service
  `Service|Configuration`; `adapter.inbound.rest` `Controller|Dto|Input|Mapper|Configuration`;
  `adapter.inbound.cibseven` `Delegate|Worker|Listener`; `adapter.outbound`
  `PersistenceAdapter|Adapter|Mapper|Entity|Repository`.
- **Spring Data types stop at the adapter.** Ports own their own `Filter`/`Page`/`Criteria` types.

No test checks it, but it holds too: **the domain has no clock.** Pass the time in; never call
`now()` in `domain/`.

## Process Glue

- A delegate extends `BaseDelegate` and overrides `executeTask`. It reads the application id from
  the business key (`execution.processBusinessKey`) and every other variable through
  `<Process>ProcessApi.FlowNodes.<Node>.Variables`, never a raw string.
- A domain exception becomes `BpmnError(Errors.<X>.code, …)` where the model has a boundary event
  for it (see `OrderBikeDelegate`).
- A `@Transactional` service behind a delegate that throws the exception the delegate maps declares
  it in `noRollbackFor` (see `OrderBikeService`). Otherwise the engine's job transaction is marked
  rollback-only and the error path never runs.
- Process variables stay minimal: ids, plus the inputs a gateway or decision reads (the DMN's `age`
  and `monthlyNetIncome`). The engine is not a database.

## BPMN Quality Gates

- `bpmn-to-code` generates typed process constants from the models at build time; a custom model
  test requires every service task to use a delegate expression (`#{beanName}`).
- Since bpmn-to-code 6 the API is node-centric: `<Process>ProcessApi.FlowNodes.<Node>` carries the
  element (`.id`, `ELEMENT_ID`), its `Variables` and its successors (`Next`). Process tests assert
  the walked path as a compile-checked `ProcessPath` (`adapter/process/ProcessPathAssertions.kt`)
  instead of hand-maintained element-id lists.
- `bpmnlint` runs on staged `.bpmn` via `.githooks/pre-commit` (install: `npm run hooks:install`).

## Testing

TDD. Match the test style to the layer:

| Layer | Test style |
|---|---|
| domain | plain unit tests |
| application service | unit tests with mocked ports |
| `adapter.inbound.rest` | `@WebMvcTest` with the use case mocked |
| `adapter.outbound.db` | `@DataJpaTest` |
| process end-to-end | CIB seven process tests, paths asserted via `ProcessPath` |

- **Mock ports, not the domain.** Build real domain objects with the test factory
  (`domain/leasing/TestObjectBuilder`).
<!-- variant:kotlin-gradle -->
- **Strict mocks, verified calls in unit tests.** Verify the exact port call, then
  `confirmVerified`; a relaxed mock lets a removed call survive the mutation gate. Process tests may
  relax the ports they do not assert on.
<!-- /variant:kotlin-gradle -->
<!-- variant:java-maven -->
- **Strict mocks, verified calls in unit tests.** Verify the exact port call, then
  `verifyNoMoreInteractions`; a lenient mock lets a removed call survive the mutation gate. Process
  tests may relax the ports they do not assert on.
<!-- /variant:java-maven -->
- **Build the expected value independently**, never from the object the code under test received.
- **Seed a persistence test directly** (`@Sql`), not through the adapter under test, and flush and
  clear before reading back.
- Tests run against H2 with Flyway off; no Postgres needed.

**Mutation testing gates PRs at 80**: a test that executes without asserting
will fail CI. Coverage says a line ran; mutation says a test would have noticed. The PR gate runs
**diff-scoped** (only the classes the PR changed, still blocking); the **full-module** gate-80 sweep
runs nightly. See [ADR-0004](docs/adr/0004-mutation-testing-as-a-blocking-pr-gate.md).

## Verify After Each Task (targeted, not a full build)

<!-- variant:blueprint -->
Verify the variant you touched — which, for a change in behaviour, is both.
<!-- /variant:blueprint -->

<!-- variant:kotlin-gradle -->
In `kotlin-gradle/`:

- Backend service/controller: `./gradlew :service:app:test --tests "*<Name>Test"`
- Architecture only: `./gradlew :service:app:test --tests "io.miragon.blueprint.ArchitectureTest*"`
  <!-- /variant:kotlin-gradle -->

<!-- variant:java-maven -->
In `java-maven/`:

- Backend service/controller: `./mvnw -pl service/app -am test -Dtest="<Name>Test" -Dsurefire.failIfNoSpecifiedTests=false`
- Architecture only: `./mvnw -pl service/app -am test -Dtest="ArchitectureTest" -Dsurefire.failIfNoSpecifiedTests=false`
  <!-- /variant:java-maven -->

From the repo root:

- Contract changed: regenerate the spec, then `git diff --exit-code openapi/openapi.json`
- Process changed: regenerate the process API, then the `adapter.process.*` tests

## Agent Tooling

Two CLIs come as Claude Code plugins (`.claude/settings.json`) and are pinned in the root
`package.json`:

- **bpmn-cli** (`npx bpmn`, skill `bpmn-cli:bpmn`) for every change to a `.bpmn`: semantic edits, no
  XML by hand, so the diagram (BPMNDI) stays intact. `BPMN_KEEP_FLOW_IDS=1` keeps flow ids stable.
  <!-- variant:blueprint -->
  Edit one variant's model, then copy the file to the other variant.
  <!-- /variant:blueprint -->
- **operate** (`npx operate`, skill `operate:operate`, `/operate:status`) for the local engine, via
  `.operate.json`. Run `npx operate guide` before using it. It reads and repairs
  (`inspect --business-key <applicationId>`, `status`, `incident`, history, `retry`). It never
  deploys: the service deploys the models from its classpath, and a hand deploy is a version the
  generated process API does not know. It never moves a case either (correlating a message,
  completing a user task): that is a business action, taken by a client of the app's API or by a
  human in the Tasklist, not by an agent.

A PostToolUse hook (`.claude/hooks/lint-bpmn.sh`) runs bpmnlint after every edit of an executable
`.bpmn` and hands the findings back.

Skills and agents live in `.claude/` (see [.claude/README.md](.claude/README.md)); other agents can read
them as recipes:

| Use | When |
|---|---|
| skill `implement-issue` | take a GitHub issue from plan (with approval) to a verified change |
| skill `automate-process` | change an executable model and its glue code, validate it |
| skill `review-change` | review a diff or PR, findings by severity |
| skill `create-adr` | record a decision in `docs/adr/` |
| agent `review-process` | read-only audit of the models against their glue code and tests |

## Working with GitHub

Use the `gh` CLI. Write everything (issues, PRs, commit messages) in **English**. Use
**Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`, `ci:`, `build:`). Commit,
push, open a PR or change an issue only when asked.

## ADRs

Architecture decisions are recorded in `docs/adr/`. Read them to understand *why* the repo is shaped
this way before proposing structural changes.

## Personality

You are a knowledgeable colleague, not someone who passively takes orders. If something proposed
doesn't look right, suggest corrections, ask critical questions, and push back where needed.
Challenge ideas that could benefit from further improvement or iterative refinement rather than just
accepting them at face value.
