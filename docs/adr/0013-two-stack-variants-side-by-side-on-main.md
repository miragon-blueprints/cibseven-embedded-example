# 0013 — Two stack variants side by side on `main`

- **Status:** Accepted
- **Date:** 2026-10-08

## Context

The blueprint serves two audiences. New projects that are free to choose should start from the stack we
consider modern: **Kotlin + Gradle**. Many enterprise teams — and our CIB seven developer trainings —
are bound to **Java + Maven** and must be able to use the blueprint without knowing Kotlin or Gradle.

Options considered for offering the Java + Maven version:

- **A movable `java-maven` git tag**, regenerated from `main` by a playbook. This was the first
  approach. Workflows and Dependabot only act on the default branch, so the tag had no CI and no
  dependency updates, and it fell behind `main` silently (eleven commits and a `bpmn-to-code` major
  version within three weeks).
- **A maintenance branch** kept current by sync pull requests. It gets CI and Dependabot
  (`target-branch`), but needs a second maintenance process and surfaces drift only when someone syncs.
- **A separate repository per stack.** Doubles the repository count of the blueprint family and makes
  sharing the process models impossible.
- **Both variants in one tree on `main`.**

Every option costs the same double implementation effort. They differ in *when* a difference between
the variants becomes visible.

## Decision

We keep **both variants on `main`**, each in its own self-contained directory, and **recommend
Kotlin + Gradle** as the default for new projects.

- `kotlin-gradle/` and `java-maven/` each hold a complete build (wrapper included) of the same
  hexagonal service. Neither depends on the other, so a fork deletes the one it does not need.
- Each variant carries its **own copy** of the language-neutral resources — BPMN, DMN, forms, Flyway
  migrations and `application.yaml` — in `service/app/src/main/resources`, where a Spring Boot developer
  expects them. A shared directory mounted by both builds was considered and rejected: it would make
  neither variant usable on its own.
- `openapi/openapi.json`, `bruno/` and `stack/` exist once and apply to both.
- A change to the service is made in **both variants in the same pull request**.

Four gates make drift visible on every pull request:

| Gate | What it proves |
|---|---|
| Build, architecture tests and PIT (gate 80) per variant | each variant is correct and tested on its own |
| One OpenAPI contract, drift-gated against both | both expose exactly the same REST API |
| The Bruno collection against both running variants | both behave the same in the end-to-end scenarios |
| `diff -r` over the two `src/main/resources` trees | both deploy the same models, schema and configuration |

Dependabot updates Gradle and Maven in the same `backend` group, so both builds move to the same
versions in one pull request.

There is deliberately **no gate that compares which files changed**. The two languages use different
patterns, so a class may exist in one variant only and a legitimate change often touches one side
alone; such a gate would mostly produce noise. The gates compare observable behaviour instead.

## Consequences

- **Positive:** the Java variant has CI, dependency updates and the automated dependency repair like
  everything else on `main`. Each variant is a complete project on its own. Trainings use a plain directory
  instead of a tag checkout.
- **Negative / trade-offs:** every functional change is implemented and reviewed twice, and pull
  requests grow. A model change has to be copied to the other variant. `main` is no longer a pure Kotlin tree, which makes it less directly comparable to
  sibling blueprints until they adopt the same layout.
- **Neutral:** the gates compare the contract and the scripted scenarios. Behaviour covered by neither
  can still differ, which is why the unit and process tests of both variants are kept case-for-case
  equivalent by review.

## Implementation notes

- Idiomatic differences are intended and not drift: records and `Optional` instead of `data` classes
  and nullable types, Mockito instead of MockK, Checkstyle instead of Konsist for the two source rules
  ([ADR-0007](0007-two-architecture-test-tools-archunit-and-konsist.md)).
- Java records carry no nullability, so the Java DTOs declare it with annotations to produce the same
  `required` arrays and nullable types the Kotlin types yield.
- The historical `java-maven` tag stays where it is as a frozen snapshot; it is no longer moved.
- Earlier ADRs name commands and paths as they were when written (`./gradlew …`, `service/app/…`);
  they now live under `kotlin-gradle/`, with Maven equivalents in `java-maven/README.md`.
