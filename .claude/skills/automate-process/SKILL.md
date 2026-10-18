---
name: automate-process
argument-hint: "[what should change in the process]"
description: "Change an executable CIB seven BPMN model of this repo (bike-leasing.bpmn, cancel-bike-order.bpmn) and wire its glue code across the hexagon: edit the model with bpmn-cli, regenerate the typed process API, add ports, services, delegates and process-adapter methods, cover the new paths with process tests and validate the model with bpmnlint and a rendered image. Use when the user asks to automate a process step, add or change a task, event or gateway, or review the diagram after an edit. Business decisions are asked, never assumed."
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, Skill, Agent
---

# Skill: automate-process

The model is the business's artifact; this skill changes it technically. The rules it relies on are in
`AGENTS.md` (Ground Rules for Changes, Process Glue, Agent Tooling); read them first.

Paths are relative to the directory that holds `service/`. Glue code and its tests live in the
package `io.miragon.blueprint` under `service/app/src/main/` and `service/app/src/test/`. The agent
`review-process` is loaded at session start; if it is not available, read
`.claude/agents/review-process.md` and apply it yourself.

<!-- variant:blueprint -->
**Two variants.** `kotlin-gradle/` and `java-maven/` each carry a copy of the models, the DMN and the
forms, and `Blueprint Checks` fails when the copies differ. Edit the model in one variant, copy the file
to the other, then regenerate, wire and test **both**.
<!-- /variant:blueprint -->

## The models

| File in `service/app/src/main/resources/` | What |
|---|---|
| `bpmn/bike-leasing.bpmn` | process `bikeLeasingProcess`: credit check, contract subprocess, insurance and bike order in parallel, handover, compensation, the withdrawal event subprocess |
| `bpmn/cancel-bike-order.bpmn` | process `cancelBikeOrder`, called by the compensation of the bike order (`callActivity_cancelBikeOrder`) |
| `dmn/check-credit-rating.dmn` | decision `checkCreditRating`, read by `businessRuleTask_checkCreditRating` |
| `forms/clarify-alternative.form`, `forms/clarify-return.form` | Camunda Forms of the two user tasks, worked in the Tasklist |

## Rules

- **Edit a `.bpmn` only through bpmn-cli** (skill `bpmn-cli:bpmn`), never as XML: `bpmn show` to read,
  one `bpmn apply` batch to change, `bpmn validate` to check (it also runs `.bpmnlintrc`). The DMN and
  the forms are edited as files; the decision id `checkCreditRating` is referenced by the model.
- **Business decisions are questions.** A new gateway condition, deadline, error path or user task
  owner comes from the issue; otherwise ask before editing.
- **Constants come from the generated API** (`BikeLeasingProcessProcessApi.FlowNodes.<Node>` with
  `.ELEMENT_ID`, `Variables`, `Next`; the shared `Messages`, `Errors`, `Escalations`, `ServiceTasks`),
  never raw strings. `adapter/process` is generated: never edit it (except
  `HistoryCleanupConfiguration`).
- **Engine limits:** a service task is a delegate by expression (`#{beanName}`, enforced by
  `adapter/process/model/ServiceTaskDelegateExpressionRule`); asynchronous continuations
  (`asyncAfter` on most tasks, `asyncBefore` on `serviceTask_orderBike`) are where retries and
  incidents happen; a compensation handler (`isForCompensation`) only runs for an activity that
  completed.

## Steps

1. **Review first.** Run the agent `review-process` on the current models and report blocking findings
   before changing anything.
2. **Agree the change.** Restate it in business terms (which element, which path, which outcome) with
   the open questions. Wait for the answer.
3. **Edit the model** with bpmn-cli, then regenerate the process API and lint:
   <!-- variant:kotlin-gradle -->
   ```bash
   (cd kotlin-gradle && ./gradlew generateBpmnModels)
   ```
   <!-- /variant:kotlin-gradle -->
   <!-- variant:java-maven -->
   ```bash
   (cd java-maven && ./mvnw -pl service/app generate-sources)
   ```
   <!-- /variant:java-maven -->
   ```bash
   npm run lint:bpmn
   ```
   Commit the regenerated `adapter/process` files with the model; CI fails when they are stale.
4. **Glue code**, skipping what exists:

   | Layer | Package (`io.miragon.blueprint.…`) | What |
   |---|---|---|
   | Domain | `domain.{leasing,bike}` | a value object per new variable (`ApplicationId`, `BikeId`, `OrderId`) |
   | Inbound port | `application.port.inbound` | one `*UseCase`/`*Query` per delegate or endpoint |
   | Service | `application.service` | one `*Service` per inbound port |
   | Delegate / listener | `adapter.inbound.cibseven` | delegate extends `BaseDelegate` (pattern `OrderBikeDelegate`); listener pattern `ClarifyAlternativeTaskListener` |
   | Outbound port | `application.port.outbound` | `LeasingProcess`: one method per message or user-task completion; `TaskInboxPort` for open tasks |
   | Process adapter | `adapter.outbound.cibseven` | `LeasingProcessAdapter`, `TaskInboxAdapter`: correlate by business key (the application id) |

   A step driven over REST also gets its controller, the re-exported `openapi.json` and a Bruno
   scenario (see `AGENTS.md` → Build Commands).
5. **Process tests.** Derive the scenarios from the model's paths: every new outgoing flow of a
   gateway, every boundary event, every event subprocess path gets a test in the test tree's
   `adapter/process/` that asserts the walked path through `ProcessPathAssertions`, a typed path from
   the generated API (see `BikeLeasingProcessTest`). A gateway's branch label must match its
   condition.
   The job executor is off in tests: `JobExecutionUtils` fires async continuations, `TimerUtils`
   fires a timer job directly. Run the `io.miragon.blueprint.adapter.process` tests (`AGENTS.md` →
   Verify After Each Task).
6. **Bruno.** A path visible over REST gets a numbered scenario folder in `bruno/`. The `.bru` files
   name element ids as strings: a renamed id is updated there too.
7. **Validate the model** with the nets in [references/visual-check.md](references/visual-check.md).
8. **Report** files created or skipped, the scenarios tested, the validation verdict and the
   questions still open. Run `review-process` once more on the result.
