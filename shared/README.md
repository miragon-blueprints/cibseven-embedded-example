# Shared assets

Everything here is **language-neutral** and exists exactly once. Both variants put this directory on
their classpath as a resource root, so the Kotlin and the Java service always deploy the same models
and the same schema — they cannot drift.

| Path | What |
|---|---|
| `bpmn/` | `bike-leasing.bpmn` and the called `cancel-bike-order.bpmn` |
| `dmn/` | `check-credit-rating.dmn`, the decision behind the business-rule task |
| `forms/` | Camunda Forms for the user tasks, rendered in the Tasklist |
| `db/migration/` | Flyway migrations — forward-only, never edit an applied one ([ADR-0010](../docs/adr/0010-flyway-for-database-migrations.md)) |

## How the builds use it

- **Gradle:** `sourceSets.main.resources.srcDir(...)` in
  [`kotlin-gradle/service/app/build.gradle.kts`](../kotlin-gradle/service/app/build.gradle.kts)
- **Maven:** a `<resource>` entry in [`java-maven/service/app/pom.xml`](../java-maven/service/app/pom.xml)

The [`bpmn-to-code`](https://github.com/emaarco/bpmn-to-code) plugin of each build reads `bpmn/*.bpmn`
from here and generates the typed process API in that variant's language.

Application configuration is deliberately **not** shared: each variant keeps its own
`service/app/src/main/resources/application.yaml`, where a Spring Boot developer expects it.

## Changing a model

1. Edit the `.bpmn` / `.dmn` / `.form` here and lint it from the repo root: `npm run lint:bpmn`.
2. Regenerate the process API in **both** variants and adapt the code that no longer compiles:
   `cd kotlin-gradle && ./gradlew generateBpmnModels` and
   `cd java-maven && ./mvnw -pl service/app generate-sources`.
