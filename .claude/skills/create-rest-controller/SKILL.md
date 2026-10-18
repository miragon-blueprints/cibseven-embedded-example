---
name: create-rest-controller
argument-hint: "[UseCase-or-Query port] [http-verb] [path]"
description: "Create or update one Spring @RestController (inbound adapter) for one inbound port, with its request types, an @Operation(operationId) and its @WebMvcTest, then carry the change through the re-exported openapi/openapi.json and a Bruno request. Use when the user asks to create a REST controller, add an endpoint, or expose a use case over REST."
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, Skill
---

# Skill: create-rest-controller

One endpoint per class in `adapter/inbound/rest/` (package `io.miragon.blueprint` under
`service/app/src/main/`), its test next to it in the test tree. The architecture rules are in
`AGENTS.md`; the contract rules in [ADR-0003](../../../docs/adr/0003-openapi-as-the-checked-in-contract.md).

## Production pattern

Copy the shape of the existing controllers instead of inventing one:

| Case | Reference |
|---|---|
| creating a case | `SubmitLeasingRequestController`: `POST /api/bike-leasing`, 200 with the new id |
| a command without a body (path variable only) | `SignContractController` |
| a command with a request body | `SelectAlternativeController` (nested `AlternativeDecisionInput`) |
| a single read | `GetLeasingApplicationController` (nested `LeasingApplicationDto`) |
| a list or page read | `ListLeasingApplicationsController` |

- **One inbound port** as the only constructor parameter; a read and a command are two controllers.
- Commands on a case are `POST /api/bike-leasing/{applicationId}/<action>` and answer **202**,
  because the engine continues asynchronously. Reads are `GET` with a `*Query` and a nested `*Dto`; a
  single read answers `ResponseEntity.notFound()` when the query finds nothing.
- Wrap every request field that has a domain value object (`ApplicationId.of(…)`, `BikeId(…)`) before
  calling the port; plain values pass through.
- **Always set `@Operation(operationId = "…")`** (camelCase). Without it springdoc falls back to the
  method name (`byId`, `list`) as the operationId. Only paths under `/api/**`
  (`springdoc.paths-to-match`) reach the spec.
- Errors become problem details in `GlobalExceptionConfiguration`: `IllegalArgumentException` → 400,
  `IllegalStateException` → 404, `MismatchingMessageCorrelationException` → 409 (the case has moved
  on). Throw those from the domain or the service; don't catch them in the controller.
<!-- variant:blueprint -->
- **Both variants export the same `openapi.json`.** Kotlin derives required and nullable from the
  types; the Java records need `@Schema(requiredMode = REQUIRED)` / `@Schema(nullable = true)` on
  their components to produce the same spec (see the Java `SelectAlternativeController`).
<!-- /variant:blueprint -->

## Test pattern

`@WebMvcTest(<Controller>.class)` with the port mocked, one test per outcome (references:
`SelectAlternativeControllerTest`, `GetLeasingApplicationControllerTest`):

- the success status with the **exact** port call, built from the domain value objects, then the
  strict check (`confirmVerified` / `verifyNoMoreInteractions`);
- each invalid input → 400, and the port is **not** called;
- for a read, every DTO field in the body (a mapping no test reads survives the mutation gate) and
  the not-found case → 404.

`GlobalExceptionConfigurationTest` covers the 409 mapping; the 400 is asserted in the controller
tests. Given/when/then comments, as in the existing tests.

## Instructions

1. **Resolve the port** from `$ARGUMENTS`, else list `application/port/inbound/` and ask. If the port
   does not exist yet, create it and its service first (a unit test with the outbound ports mocked).
2. **Agree verb, path and status codes** with the user.
3. **Write the test first**, then the controller; for an existing file add only what is missing. Run
   it by name (`AGENTS.md` → Verify After Each Task).
4. **Carry the contract through:** regenerate `openapi/openapi.json` with the `OpenApiSpecExportTest`
   (`AGENTS.md` → Build Commands) and review `git diff -- openapi/openapi.json`: the new path with its
   operationId and schemas. Never hand-edit the file.
5. **Add the request to `bruno/`**: in the scenario folder of the flow, numbered in order, with
   assertions on the status and the body; a new flow gets a new folder and a row in
   `bruno/README.md`.
<!-- variant:blueprint -->
6. **Both variants:** the same controller and test in `kotlin-gradle/` and `java-maven/`; both must
   export a byte-identical `openapi.json`.
<!-- /variant:blueprint -->
