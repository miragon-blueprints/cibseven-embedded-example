# Shared assets

The **language-neutral** process assets. The build mounts this directory as a resource root, so the
service deploys exactly these models and this schema.

<!-- variant:blueprint -->
Both variants consume it, which is why the Kotlin and the Java service cannot drift apart here.
Application configuration is deliberately **not** shared: each variant keeps its own
`service/app/src/main/resources/application.yaml`, where a Spring Boot developer expects it.
<!-- /variant:blueprint -->

| Path | What |
|---|---|
| `bpmn/` | `bike-leasing.bpmn` and the called `cancel-bike-order.bpmn` |
| `dmn/` | `check-credit-rating.dmn`, the decision behind the business-rule task |
| `forms/` | Camunda Forms for the user tasks, rendered in the Tasklist |
| `db/migration/` | Flyway migrations — forward-only, never edit an applied one ([ADR-0010](../docs/adr/0010-flyway-for-database-migrations.md)) |

## How the build uses it

<!-- variant:kotlin-gradle -->
- **Gradle:** `sourceSets.main.resources.srcDir(...)` in
  [`kotlin-gradle/service/app/build.gradle.kts`](../kotlin-gradle/service/app/build.gradle.kts)
<!-- /variant:kotlin-gradle -->
<!-- variant:java-maven -->
- **Maven:** a `<resource>` entry in [`java-maven/service/app/pom.xml`](../java-maven/service/app/pom.xml)
<!-- /variant:java-maven -->

The [`bpmn-to-code`](https://github.com/emaarco/bpmn-to-code) plugin reads `bpmn/*.bpmn` from here and
generates the typed process API.

## Changing a model

Edit the `.bpmn` / `.dmn` / `.form` here, lint it from the repo root with `npm run lint:bpmn`, then
regenerate the process API and adapt the code that no longer compiles:

<!-- variant:kotlin-gradle -->
```bash
cd kotlin-gradle && ./gradlew generateBpmnModels
```
<!-- /variant:kotlin-gradle -->
<!-- variant:java-maven -->
```bash
cd java-maven && ./mvnw -pl service/app generate-sources
```
<!-- /variant:java-maven -->
