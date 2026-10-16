# MiraVelo bike leasing (CIB seven embedded)

[![Pre-Merge Kotlin/Gradle](https://github.com/miragon-blueprints/cibseven-embedded-example/actions/workflows/pre-merge-kotlin-gradle.yml/badge.svg)](https://github.com/miragon-blueprints/cibseven-embedded-example/actions/workflows/pre-merge-kotlin-gradle.yml) [![Pre-Merge Java/Maven](https://github.com/miragon-blueprints/cibseven-embedded-example/actions/workflows/pre-merge-java-maven.yml/badge.svg)](https://github.com/miragon-blueprints/cibseven-embedded-example/actions/workflows/pre-merge-java-maven.yml) [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A solution template to fork for automating a business process on [CIB seven](https://cibseven.org)
(the community fork of Camunda 7): one complete, production-shaped BPMN service on an **embedded
engine** with Spring Boot. It is headless — driven over REST, with the CIB seven webapps for the
human steps — and still a work in progress.

<!-- variant:blueprint -->
## Pick your stack

| | [`kotlin-gradle/`](kotlin-gradle/README.md) | [`java-maven/`](java-maven/README.md) |
|---|---|---|
| **Stack** | Kotlin 2.4 · Gradle | Java 21 · Maven |
| **Choose it when** | you are free to choose — **our recommendation** | Java + Maven is your standard, or you are in a training |

Both run the same process behind the same REST contract; CI keeps their models and configuration
identical. Building on one? [Turn the repo into a single-stack starter](docs/starter.md).
<!-- /variant:blueprint -->

## The process

![The bike-leasing process](docs/assets/bike-leasing.png)

**MiraVelo** is a fictional bike brand that sells on a leasing model. The service takes a leasing
application from the first request to an active lease and deliberately covers the BPMN elements you
meet in real processes: message start, JavaDelegate service tasks and a DMN rule, an embedded
sub-process with an event-based gateway and reminder timer, a parallel fork/join, a user task with a
Camunda Form, execution and task listeners, compensation guarded by error and escalation events, a
call activity, a withdrawal event sub-process and a terminate end event.

## Quick start

Requires JDK 21 and Docker or Podman.

```bash
docker compose -f stack/docker-compose.yml up -d   # PostgreSQL on :5432
# variant:kotlin-gradle
cd kotlin-gradle && ./gradlew :service:app:bootRun   # service + embedded engine on :8081
# /variant:kotlin-gradle
# variant:java-maven
cd java-maven && ./mvnw -DskipTests install && ./mvnw -pl service/app spring-boot:run   # service + embedded engine on :8081
# /variant:java-maven
```

| What | URL | Login |
|---|---|---|
| CIB seven webclient (Cockpit, Tasklist) | <http://localhost:8081/webapp/#/seven/auth/start> | admin / admin |
| Classic Cockpit / Tasklist | <http://localhost:8081/camunda> | admin / admin |
| Swagger UI | <http://localhost:8081/swagger-ui.html> | |

Drive the whole process over REST: `cd bruno && npx --yes @usebruno/cli@4.0.0 run . --env local -r`.
Tests, ports and containers are in [CONTRIBUTING.md](CONTRIBUTING.md).

## What's inside

| Path | Content |
|---|---|
| [`openapi/`](openapi/openapi.json) | the checked-in, drift-gated REST contract |
| [`bruno/`](bruno/README.md) | REST scenarios, the two ways to complete a user task, the incident demo |
| [`stack/`](stack/docker-compose.yml) | the Postgres dev stack |
| [`docs/`](docs/README.md) | the Architecture Decision Records |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md).

## License

[MIT](LICENSE) © Miragon GmbH.
