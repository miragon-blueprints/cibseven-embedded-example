# Java + Maven variant — regeneration playbook

The **Java 21 + Maven** version of this blueprint is published at the movable git tag **`java-maven`**.
It is not a hand-maintained fork: it is **regenerated from Kotlin `main`** by following this procedure,
then the tag is force-moved to the result. `main` stays the Kotlin/Gradle greenfield reference —
comparable across the blueprint family — so the Java/Maven version is generated, never merged into `main`.

> Sync is by **discipline, not a gate**. Re-run this after significant `main` changes or at release. The
> tag may trail `main` between runs; its commit message records the `main@<sha>` it came from.

## Use the variant

```bash
git fetch --tags
git worktree add ../java-maven java-maven   # or: git checkout java-maven
```

Build and run exactly as the Kotlin blueprint, with Maven instead of Gradle:

```bash
docker compose -f stack/docker-compose.yml up -d   # Postgres
mvn -pl service/app spring-boot:run                 # backend + engine on :8080
mvn clean verify                                    # arch + unit + process + spec + PIT
```

## Regenerate (the transformation)

Start from a clean checkout of current `origin/main` (Kotlin/Gradle). Apply the port below — the process
model, DMN, Camunda Forms, Flyway schema, hexagonal structure, the OpenAPI paths/DTOs and the Bruno
scenarios are **unchanged**; only the implementation language and build system change.

**Build system — Gradle → Maven**
- Replace `settings.gradle.kts`, the `build.gradle.kts` files, the wrapper and `gradle/libs.versions.toml`
  with a multi-module Maven build: root `pom.xml` + `service/app` + `service/common-architecture-tests`.
- Spring Boot parent (4.x); pin all third-party versions (CIB seven 2.2, springdoc, `bpmn-to-code`).
- OCI image stays `mvn -pl service/app spring-boot:build-image` (image `miravelo/cibseven-embedded-example`).

**Language — Kotlin → Java 21**
- `data`/`value` classes → **records**; `copy()` → explicit `with*` transitions; companion objects → statics.
- `kotlin-logging` → **SLF4J**; inline Kotlin extension helpers into their adapter (drop `ProcessEngineExtensions`).
- Port finder methods returning `T?` → `Optional<T>`.

**Generated process API**
- Still generated, now by the **`bpmn-to-code` Maven plugin** (`io.miragon:bpmn-to-code-maven`, goal
  `generate-bpmn-api`, `outputLanguage=JAVA`) into `src/main/java`, bound to `generate-sources`. Never hand-edited.

**Quality gates**
- **ArchUnit** suite ports verbatim — it is bytecode-based and language-neutral.
- **Konsist** (Kotlin-source-only) is dropped; its two source rules (no wildcard imports, one top-level
  type per file) move to **maven-checkstyle-plugin** (`config/checkstyle/checkstyle.xml`), failing the build.
- Tests: `mockk`/`springmockk` → **Mockito + Spring `@MockitoBean`**; remove JGiven (unused).
- **Mutation testing:** `pitest-maven`, gate 80 (diff-scoped on PRs, full nightly).

**Also update**
- CI (`pre-merge.yml`, `nightly.yml`), `dependabot.yml` (gradle → maven ecosystem), `.conductor/settings.toml`,
  and the prose: `README`, `CONTRIBUTING`, `AGENTS.md` (stack, build commands, ports).

**Known caveat — carry it forward.** `openapi/openapi.json` regenerated from the Java controllers keeps
every path, `operationId`, DTO name and field, but loses the `required` arrays and `["type","null"]`
unions the null-aware Kotlin types produced (Java records carry no nullability metadata). If downstream
client generation needs them back, restore with `@Schema`/Bean-Validation annotations.

## Verify

```bash
mvn clean verify                        # tests + ArchUnit + Checkstyle + repackage; PIT ≥ 80
git diff --exit-code openapi/openapi.json   # contract matches the code
```

Boot against Postgres and confirm `/actuator/health` UP, `POST /api/bike-leasing` → 200, `GET /api/bikes` → 200.

## Publish (move the tag)

Tag the regenerated tree as a single commit on top of the `main` commit it derives from, then force-move
the tag:

```bash
SRC=$(git rev-parse origin/main)                     # the main commit you generated from
TREE=$(git rev-parse HEAD^{tree})                    # the regenerated tree
COMMIT=$(git commit-tree "$TREE" -p "$SRC" -m "chore: Java 21 + Maven variant regenerated from main@${SRC:0:7}")
git tag -f java-maven "$COMMIT"
git push -f origin java-maven
```

There is no merge or cherry-pick — each regeneration replaces the tag wholesale.
