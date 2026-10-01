# Spike — running the embedded CIB seven engine as a GraalVM native image

> **Status:** spike on the local branch `spike/spring-native`, 2026-10-01. Not merged, not pushed.
> **Result:** the service — REST API, embedded engine, `/engine-rest`, Cockpit / Tasklist, actuator —
> runs as a native executable on macOS/arm64 and passes the same end-to-end scenarios as the JVM build:
> ready in 0.7–0.9 s instead of 4.5–5 s, with 340–410 MB instead of 940–1000 MB of resident memory.
> The Linux container build is configured but **was not executed** (see [Limitations](#10-limitations-workarounds-and-risks)).

"Spring Native" here means the AOT / GraalVM native-image support built into Spring Boot 3+ — not the
retired `spring-native` project.

Three kinds of statements appear below and are marked as such:

- **Observed** — measured or reproduced in this spike, with the command that shows it.
- **Documented** — taken from official Spring Boot / GraalVM documentation.
- **Conclusion** — our own judgement.

## 1. Starting point and baseline

**Observed** on the unchanged `main` (`9a55373`):

| Aspect | Baseline |
|---|---|
| Build | Gradle 9.7.1 (wrapper), Kotlin DSL, version catalog; modules `:service:app` and `:service:common-architecture-tests` |
| Language / runtime | Kotlin 2.4.0, JVM target 21 |
| Framework | Spring Boot 4.1.1 (Spring Framework 7.0.9), Hibernate 7.4.5, Flyway 12.4.0, springdoc 3.1.1 |
| Engine | CIB seven 2.2.0, embedded through `cibseven-bpm-spring-boot-starter-webapp-4` and `-rest-4`; MyBatis 3.5.15, Jersey 4.0.2 / HK2 4.0.0-M3, FEEL-Scala 1.19.3 |
| Database | PostgreSQL 18.6 (driver 42.7.13); H2 in tests |
| Entry points | `CibsevenBikeLeasingApplication.main`; a process instance starts by message through `POST /api/bike-leasing` |
| Engine resources | `bpmn/bike-leasing.bpmn`, `bpmn/cancel-bike-order.bpmn`, `dmn/check-credit-rating.dmn`, `forms/clarify-alternative.form`, `forms/clarify-return.form`, picked up by `camunda.bpm.deployment-resource-pattern` (classpath scan) |
| Engine code | 12 `JavaDelegate`s, one `ExecutionListener`, one `TaskListener` (Spring beans referenced by `#{bean}`), one `ProcessEnginePlugin` (history clean-up) |
| BPMN palette | message start / catch / end events, event-based gateway, interrupting and non-interrupting timers, error and escalation boundary events, compensation with three handlers, call activity, message event sub-process, terminate end, parallel gateway, two user tasks with deployed Camunda Forms, DMN business-rule task (FEEL), `failedJobRetryTimeCycle`. No scripts, no multi-instance, no signals |
| Expressions | delegate expressions, JUEL conditions (`${!solvent}` …), `#{execution.processBusinessKey}` |
| Process variables | `String`, `Integer`, `Double`, `Boolean` only; the DMN result passes through Java serialization inside the engine |
| Persistence | JPA (two entities, Spring Data repositories) + Flyway for the domain; MyBatis for the engine; one Spring transaction manager for both |
| External interfaces | 8 domain REST paths (`openapi/openapi.json`), `/engine-rest` (Jersey), Cockpit / Tasklist / Admin under `/camunda`, actuator, Swagger UI |
| Tests | 140 tests in 62 classes: domain, services (MockK), `@WebMvcTest`, `@DataJpaTest`, process tests (`@SpringBootTest` + MockK + bpm-assert), ArchUnit, Konsist, model validation, OpenAPI export; PIT gate 80 |
| End-to-end | 7 Bruno folders, 45 requests, against a running stack |

Dynamic mechanisms the engine relies on — the part that matters for a native image: reflection
(MyBatis / OGNL on entities and query objects, JUEL bean resolution, `Class.newInstance()` for loggers,
exceptions and commands, Jackson on REST DTOs, HK2 injection), JDK proxies (MyBatis statement
logging), Java serialization (DMN results, object variables), `ServiceLoader` and hand-read
`META-INF/services` files (script engines, Cockpit plugins, Jersey), classpath scanning (model
auto-deployment), resources loaded by name (mapper XML, SQL schema scripts, XSDs, DTDs), XML parsing
with schema validation.

Baseline run (commands in sections 4 and 7):

| Check | Result |
|---|---|
| `./gradlew clean build --no-daemon` | green, 140 tests, 0 failures, 94 s |
| `java -jar app-1.0-SNAPSHOT.jar` against a fresh Postgres | started in 4.97 s (Spring's own figure) |
| Bruno, all folders | 45 / 45 requests, 80 / 80 assertions, 25 s |
| Cockpit login + plugin API | works |

Warnings already present in the baseline, unrelated to the migration: two
`PropertySourcesPlaceholderConfigurer` beans, an explicitly configured Hibernate dialect
(`HHH90000025`), `spring.jpa.open-in-view`, springdoc's "enabled by default" notes, and
`ENGINE-09032` (no `jackson.properties` on the classpath).

## 2. Versions and compatibility decisions

| Component | Before | After | Why |
|---|---|---|---|
| Spring Boot / Framework | 4.1.1 / 7.0.9 | unchanged | already the native-capable generation |
| Kotlin, JVM target | 2.4.0, 21 | unchanged | bytecode 21 compiles fine on a 25 toolchain |
| CIB seven | 2.2.0 | unchanged | no newer release; no native support upstream at any version |
| Gradle | 9.7.1 | unchanged | |
| Native Build Tools plugin | — | 1.1.8 | the version Spring Boot 4.1.1 is tested with |
| GraalVM | — | Community 25.0.2 (`25.0.2+10-jvmci-b01`) | Spring Boot 4 needs GraalVM 25 |
| Everything else | | unchanged | no dependency was upgraded or removed |

**Documented:** Spring Boot 4.1.1 lists "GraalVM Community 25" and "Native Build Tools 1.1.8" as the
supported native toolchain and states that applications "can be converted into Native Images using
GraalVM 25 or above"
([system requirements](https://docs.spring.io/spring-boot/system-requirements.html)).

**Observed:** CIB seven publishes no reachability metadata and nothing about native images
(`cibseven/cibseven` at `v2.2.0`: no `META-INF/native-image`, no issue on the topic). Its Quarkus
extension has no native build steps either. MyBatis 3.5.15 is not in the
[GraalVM reachability metadata repository](https://github.com/oracle/graalvm-reachability-metadata);
Jersey 4.0.2 ships partial metadata for its core modules only.

**Conclusion:** no version change was needed. The whole migration is build configuration plus the
glue in `adapter/process/nativeimage`.

## 3. Local prerequisites

| For | Needs |
|---|---|
| JVM build and run | JDK 21, Docker or Podman for Postgres — as before |
| Native build | GraalVM 25 (Community or Oracle) with `native-image`; `GRAALVM_HOME` pointing at it. On macOS also the Xcode command line tools. About 15–20 GB of free RAM: the builds peaked between 14 and 16.5 GB here |
| Acceptance run | `curl`, `jq`, `npx` (Bruno CLI) |
| Native container build | a container engine whose VM has well over 8 GB of RAM (see section 6) |

```bash
# e.g. with SDKMAN
sdk install java 25.0.2-graalce
export GRAALVM_HOME="$HOME/.sdkman/candidates/java/25.0.2-graalce"
```

In this spike GraalVM was unpacked into the git-ignored `.context/` folder instead, so nothing was
installed system-wide.

## 4. JVM build and start — unchanged

```bash
docker compose -f stack/docker-compose.yml up -d
./gradlew build                      # all tests, as before
./gradlew :service:app:bootRun       # or: java -jar service/app/build/libs/app-1.0-SNAPSHOT.jar
```

**Observed:** without `-Pnative` the build is identical to the baseline — the native plugin is not
applied, no AOT processing runs, the jar contains no AOT classes, `bootBuildImage` still builds the
JVM image. `./gradlew build` is green with 176 tests, 36 of them new.

## 5. Native build, native tests, start

Everything native hangs off one Gradle property, the counterpart of Maven's `native` profile:

```bash
export GRAALVM_HOME=/path/to/graalvm-25      # JAVA_HOME may stay on JDK 21 or point at GraalVM too

./gradlew -Pnative :service:app:processAot      # Spring AOT processing only
./gradlew -Pnative :service:app:aotTest         # native-tagged tests on the JVM in AOT mode (seconds)
./gradlew -Pnative :service:app:nativeCompile   # -> service/app/build/native/nativeCompile/app
./gradlew -Pnative :service:app:nativeTest      # builds and runs the native test image

service/app/build/native/nativeCompile/app      # start it; same env vars as the jar
```

| Task | What it proves |
|---|---|
| `processAot` | the application context can be computed at build time |
| `aotTest` | the AOT-generated context actually works — still on the JVM, so it fails fast |
| `nativeTest` | the process runs inside a native image: 7 scenarios, no mocks, on H2 |
| `scripts/e2e.sh native` | the native executable behaves like the JVM service from the outside (section 7) |

**Which tests run natively.** Mocking libraries generate classes at run time, which a native image
cannot do, so the 140 existing tests stay JVM-only — untouched and still run by `./gradlew build`.
`BikeLeasingEndToEndTest` was added for the native lane: it drives the deployed process through the
real use cases, delegates, listeners, DMN table and JPA adapters. It is tagged `native`; with
`-Pnative` the `test` task runs only that tag, and `nativeTest` compiles exactly those tests into the
test image. Without `-Pnative` it is simply one more JVM test.

**Documented:** this split is what Spring recommends — "use JVM for majority of unit and integration
tests … focus native image testing on areas likely to differ"
([testing native applications](https://docs.spring.io/spring-boot/how-to/native-image/testing-native-applications.html)).

## 6. Native container build — configured, not executed

```bash
./gradlew -Pnative :service:app:bootBuildImage
# -> miravelo/cibseven-embedded-example-native:1.0-SNAPSHOT
```

With `-Pnative`, `bootBuildImage` gets its own image name and passes `BP_NATIVE_IMAGE=true` and
`BP_JVM_VERSION=25` to the Paketo buildpack; the JVM lane keeps
`miravelo/cibseven-embedded-example` and `BP_JVM_VERSION=21`. For Podman the socket note in
CONTRIBUTING.md applies unchanged.

**Observed:** the effective task configuration was verified for both lanes. The build itself was
**not run**: the only container engine on the spike machine is a Podman VM with 7.4 GB of RAM, of
which other running workloads use 3.6 GB. The macOS native build peaked at 14 GB and more; inside
that VM it would have failed or pushed the neighbouring containers into the OOM killer.

**Conclusion:** the Linux image is the one that would be deployed, and it is unverified. A macOS
executable proves the hints and the AOT glue, not Linux-specific behaviour. This is the first thing to
close before drawing any production conclusion — in CI or on a machine with a 16 GB container VM.

## 7. End-to-end acceptance run

`scripts/e2e.sh` starts the application, waits for readiness and checks it from the outside. It is the
same script for both worlds, which is what makes the comparison meaningful:

```bash
docker compose -f stack/docker-compose.yml up -d

./gradlew :service:app:bootJar                   && scripts/e2e.sh jvm
./gradlew -Pnative :service:app:bootJar          && scripts/e2e.sh jvm-aot   # JVM, AOT mode
./gradlew -Pnative :service:app:nativeCompile    && scripts/e2e.sh native

# busy ports? the application's own env vars are honoured
SERVER_PORT=18080 SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/bikeleasing scripts/e2e.sh native
```

| Section | Covers |
|---|---|
| Operational surface | actuator health / liveness / readiness / info / prometheus, OpenAPI, Swagger UI, engine REST, deployed definitions, decision and forms, seeded catalogue |
| CIB seven webapps | Welcome, Tasklist, Cockpit pages, static assets, the plugin scripts of Cockpit / Tasklist / Admin, admin login, Cockpit plugin API, Tasklist API |
| Bruno scenarios | all 7 folders: happy path, escalation, abort (compensation, call activity, form-only user task), not solvent (DMN), bike unavailable (user task, task listener), incident demo (retries, incident), list and inbox |
| Engine read APIs | 41 query, statistics, report, history and identity endpoints — far more MyBatis mappings than the scenarios touch |
| User task form | the task resolves its deployed Camunda Form through `/engine-rest`, then is completed through the domain API |
| Restart | an instance is started, the application is stopped and started again, the instance is driven to its end |

**Observed** (fresh database for every run):

| Variant | Failed checks |
|---|---|
| `jvm` | 0 |
| `jvm-aot` | 0 |
| `native` | 0 |

A wider comparison outside the script — 144 `GET` requests against `/engine-rest`, the Cockpit / Admin
/ Tasklist APIs, static assets and actuator, once against the JVM and once against the native
executable on the same database — returned the same status code and body type for every request
(119 × 200, the rest identical 4xx / 5xx on both sides).

Cockpit and Tasklist were also opened in a browser against the native executable: login, the process
definition view with the rendered diagram, instance list and incident badge, creating a Tasklist
filter, and opening the waiting user task with its rendered Camunda Form. That click-through found the
one defect none of the HTTP checks had caught — the missing plugin scripts described in section 9 —
which is why they are part of the script now.

### Process coverage, by evidence

| Capability | `nativeTest` | `e2e.sh native` |
|---|---|---|
| Start by message through the REST API | start through the use case | ✓ |
| Service tasks (12 delegates), execution listener, task listener | ✓ all | ✓ all but the reminder mail |
| Gateways and JUEL conditions | ✓ | ✓ |
| DMN decision (FEEL) | ✓ rejection and acceptance | ✓ |
| User tasks: read, complete, deployed form | ✓ complete | ✓ incl. form |
| Message correlation (contract signed, handover, withdrawal) | ✓ | ✓ |
| Timers: 14-day deadline, withdrawal period | ✓ fired by hand | ✓ fired through REST |
| Non-interrupting 7-day reminder timer | ✓ fired by hand | — |
| Job executor, asynchronous continuations | driven by hand | ✓ real job executor |
| BPMN error boundary (invalid application) | ✓ | — |
| Escalation | ✓ | ✓ |
| Compensation, call activity, message event sub-process | ✓ | ✓ |
| Terminate end event (no alternative found) | — | — |
| Technical failure, retries, incident | ✓ | ✓ `R3/PT10S` in real time |
| Schema creation, Flyway migration, auto-deployment on an empty database | H2, `create-drop` | ✓ Postgres |
| Restart with a running instance | — | ✓ |
| Signals, multi-instance | not in the model | not in the model |

The terminate end event is the one modelled path without native evidence. It has none on the JVM
either: no existing test or Bruno scenario reaches it, and reaching it currently fails for a reason
unrelated to this spike (see the incidental finding in section 10).

## 8. Runtime hints

All hints are contributed through `RuntimeHintsRegistrar`s imported by `NativeImageConfiguration`, so
they take part in Spring's AOT processing and end up in the generated
`reachability-metadata.json`. There is **no hand-written JSON and no tracing-agent output** in the
repository.

| Registrar | Opens | Because |
|---|---|---|
| `CibSevenRuntimeHints` | every class under `org.cibseven` except the model API (`org.cibseven.bpm.model`) — about 4,000 types — for reflection, the serializable ones also for Java serialization; `*.xml`, `*.sql`, `*.xsd`, `*.properties` under `org/cibseven`; the webapp plugin assets under `plugin/`; the `META-INF/services` files of CIB seven, the script engines and FEEL; the cron message bundle; `SecureRandom` and the FEEL script engine factories by name; common JDK value types for serialization | CIB seven has no metadata. MyBatis and OGNL read entities and query objects named in mapper XML, JUEL resolves bean properties of whatever an expression touches, Jackson binds REST DTOs, loggers / exceptions / commands / plugins are instantiated by class name, DMN results and object variables are Java-serialized |
| `MyBatisRuntimeHints` | the log adapter, language drivers and `Configuration` by name; public methods of `String` and the collection interfaces; JDK proxies for the JDBC interfaces; the bundled DTDs; the shaded Javassist `ProxyFactory` type | MyBatis has no metadata. OGNL conditions in the mapper XML call `equals`, `contains`, `isEmpty`; statement logging at DEBUG wraps JDBC in proxies; the configuration refuses to start unless Javassist is found by name (lazy loading is off, nothing is generated at run time) |
| `JerseyRuntimeHints` | every class under `org.glassfish.jersey` and `org.jvnet.hk2` — about 2,000 types; three Jackson JAX-RS types; Jersey's `META-INF/services` files and message bundles | `/engine-rest` and the webapp backends are JAX-RS applications. HK2 builds and injects providers reflectively, Jersey reads its service files itself. The metadata Jersey ships left gaps in every start-up attempt |
| `EnversRuntimeHints` | `DefaultRevisionEntity`, `RevisionMapping`, `EnversRevisionRepositoryImpl` | Envers arrives transitively with the CIB seven webclient. Hibernate then maps the revision entity, and Spring Boot builds every JPA repository on the Envers base class; published metadata covers neither |
| `ProcessModelRuntimeHints` | `bpmn/*.bpmn`, `dmn/*.dmn`, `forms/*.form` | the auto-deployment scans the classpath; a native image only contains resources it was told about |

How the list came about:

1. **Observed:** with Spring's AOT output and the metadata repository alone, the executable built but
   failed at the first engine logger (`NoSuchMethodException: ProcessEngineLogger.<init>()`).
2. The GraalVM tracing agent was run once, on the JVM in AOT mode, under the full acceptance run. Its
   output — 3,306 reflection entries, 415 resources — was used **as a map, not as configuration**: fed
   in unfiltered it broke the build's Hibernate setup, and it only knows the paths that were executed.
3. The agent output was diffed against the metadata already on the build path. What remained fell into
   the five groups above and was turned into rules; each rule was then confirmed or corrected by a
   native run.

**Documented:** this is the route Spring describes — implement `RuntimeHintsRegistrar`, activate it
with `@ImportRuntimeHints`, test it with `RuntimeHintsPredicates`; "Spring itself doesn't contain hints
for 3rd party libraries and instead relies on the reachability metadata project"
([advanced native image topics](https://docs.spring.io/spring-boot/reference/packaging/native-image/advanced-topics.html)).

**Conclusion — why package-wide rules.** Opening 6,000 types is broad, and that is deliberate. The
engine's reflection is not confined to a few call sites: any query object can become a MyBatis
parameter, any entity can appear in a JUEL expression, any DTO in a REST response. A list of exactly
the classes the agent saw would pass this repository's scenarios and fail on the first Cockpit page
or REST endpoint nobody exercised. Package rules survive that, and they survive a CIB seven upgrade
without edits. The price is image size: 343 MB for an (unusable) image without these hints against
407 MB with them.

The hints are covered by `CibSevenRuntimeHintsTest`, `LibraryRuntimeHintsTest` and `ClasspathScanTest`
(JVM unit tests, mutation score of the package 98 %), and end to end by `nativeTest` and
`scripts/e2e.sh native`.

## 9. Changes to engine wiring, configuration and resources

No process model, no delegate, no domain or application code and no `application.yaml` setting was
changed. Three things had to be added in `adapter/process/nativeimage`, and one to the build:

**1. `AotHiddenInjectionBeanPostProcessor` — CIB seven beans that Spring AOT wires incompletely.**
**Observed:** in AOT mode (JVM or native) the engine fails with
`NullPointerException: … "this.camundaBpmProperties" is null`. Spring AOT generates injection code
from the type a `@Bean` method *declares*. CIB seven declares nine such beans by interface or base
class — `CamundaProcessEngineConfiguration`, `CamundaDatasourceConfiguration`, …, and the webclient's
`BpmProvider` and `BaseUserProvider` — while the instances carry `@Autowired`, `@Value` and
`@PostConstruct` members. The post-processor replays annotation-driven injection for exactly those
beans, only in AOT mode. `AotHiddenInjectionBeansTest` walks the running context and fails the JVM
build if an upgrade adds another such bean.

**2. `ClasspathDeploymentConfiguration` — auto-deployment without `java.io.File`.**
**Observed:** on an empty database the native executable started cleanly and deployed nothing; the
first request failed with "No process definition matches". The starter
(`DefaultDeploymentConfiguration.isFile`) and the engine
(`SpringTransactionsProcessEngineConfiguration.getFileResourceName`) both call `Resource.getFile()` on
the scanned models. The resource file system of a native image has no `File` view; the starter catches
the `UnsupportedOperationException` and returns an empty set. The replacement filters through
`Resource.isReadable` and the URL, and hands models from such a file system to the engine as in-memory
resources named by file name — the same name they get when deployed from the fat jar. On the JVM the
behaviour is unchanged; a failing scan now stops the start-up instead of being logged.

**3. The registrars from section 8.**

**4. Webapp plugin assets (build).** **Observed:** in a browser, Cockpit stayed blank on the native
executable — `/camunda/api/cockpit/plugin/cockpitPlugins/static/app/plugin.js` answered 404, while
every page, the login and the plugin REST API worked. The webapps read their plugin scripts through
`ServletContext.getResourceAsStream`, which Tomcat serves from `META-INF/resources` of the webjar; a
native image has no jars to mount. The webapp's own fallback is a classpath lookup under
`plugin/<app>/…`, so the native lane copies the assets from the webjar to that path at build time.

Build configuration (`service/app/build.gradle.kts`, `gradle/libs.versions.toml`):

- `org.graalvm.buildtools.native` is declared but only applied with `-Pnative`.
- With `-Pnative`: `spring-boot-devtools` is excluded; the plugin assets are copied (see 4.); the
  `test` task filters on the `native` tag; `aotTest` is registered; the test image ignores the broken
  `native-image.properties` inside `kotlin-compiler-embeddable` (pulled in by Konsist);
  `bootBuildImage` switches to the native image.

## 10. Limitations, workarounds and risks

**Not verified**

- **Linux and the container image** (section 6). Highest-priority gap.
- **Cockpit / Tasklist beyond a short click-through.** Two views of each were opened in a browser
  (section 7). The one defect that turned up there was invisible to every HTTP-level check, so
  further views — Admin, history, batch operations, migration — may hide more of the same kind.
- **Throughput under load.** Only start-up, memory and the duration of the scenario run were measured.
  A native image has no JIT, and GraalVM Community offers only the serial collector.

**Fixed at build time**

- **Documented:** with AOT "bean definitions cannot change at runtime"; `@Profile` and
  `@ConditionalOnProperty` are evaluated during the build
  ([introducing native images](https://docs.spring.io/spring-boot/reference/packaging/native-image/introducing-graalvm-native-images.html)).
  For this service that freezes, among others, `camunda.bpm.job-execution.enabled`,
  `camunda.bpm.webapp.enabled`, `camunda.bpm.admin-user.id` (present or not) and the active profiles.
  Plain values — datasource URL and credentials, server port, job executor timings — are still read
  at start-up; the acceptance run overrides port and datasource through environment variables.

**Engine features outside this model**

- **Object process variables.** Java-serialized variables of application types need a serialization
  hint per type; without it the engine fails when it stores the variable. Primitive variables and the
  DMN result are covered. Spin (JSON / XML variables) is not on the classpath and was not looked at.
- **Script tasks.** JUEL and FEEL work. Groovy, JavaScript or Python scripts compile code at run time
  and cannot work in a native image.
- **Deployments at run time.** Deploying models through REST or Cockpit goes through the same parser
  as the auto-deployment and should work, but was not tested. Delegates referenced by class name
  instead of by bean would need hints.

**Maintenance**

- The workarounds depend on CIB seven internals: the list of nine beans and the two `getFile()` call
  sites. Both are guarded — a test for the bean list, the acceptance run for the deployment — but
  every CIB seven upgrade needs the native lane to be re-run, not just the JVM build.
- Nothing here is supported by CIB seven. A defect that only shows natively is ours to analyse.
- Hints for anything outside `org.cibseven`, Jersey and MyBatis are found by running into them. A
  missing registration is not a build error: it surfaces at run time, and only on the path that needs
  it — the silent non-deployment in section 9 is the example.

**Toolchain observations**

- **Observed:** `--exact-reachability-metadata`, which would turn missing registrations into
  errors instead of silent fallbacks, aborts the build with
  `VMError: Bulk queries can only be set with 'name'` — caused by a legacy configuration file in one of
  the third-party jars. It could not be used as a safety net.
- **Observed:** the native-image builder crashed twice with a `SIGSEGV` in
  `SystemDictionary::resolve_or_null`, in 2 of the first 15 builds, while the hints still named
  classes with unresolvable supertypes (OSGi, JUnit 3). None of the 11 builds after the scan started
  skipping those crashed — too few to call it fixed. Treat GraalVM Community 25.0.2 with some caution
  for images of this size.
- **Observed:** native-only log noise — Flyway warns "Unable to scan location: /db/migration
  (unsupported protocol: resource)" although the migration runs (Spring Boot registers its own
  `NativeImageResourceProvider`), and Micrometer reports that GC notifications are unavailable.
- **Observed:** one `nativeTest` run took 19 minutes instead of 3 — a 16-minute stall between the JVM
  test step and the start of the image build, while the image build itself reported its usual 2.5
  minutes. It did not reproduce; the machine was under load from unrelated builds at the time
  (load average 25), which is the likely but unproven cause.
- A native build takes minutes, not seconds, and needs far more memory than the JVM build
  (section 11). It belongs in CI, not in the inner development loop.

**Incidental finding, not caused by the migration.** Completing `Clarify alternative with customer`
with `alternativeFound=false` after the first order found no stock leaves the instance with a failed
job (`null cannot be cast to non-null type kotlin.String`): the compensation calls
`cancel-bike-order` with an `orderId` that was never set. It reproduces on the JVM, in code this spike
did not touch, and was left alone.

## 11. Measurements

One machine, a handful of runs — an observation of this spike, not a benchmark.

- Apple M5 Pro, 18 cores, 64 GB RAM, macOS 27.0 (arm64)
- JVM: Temurin 21.0.12 (`java -jar`, default flags). Native: GraalVM CE 25.0.2, default build options
  (`-O2`, serial GC), no tuning on either side
- PostgreSQL 18.6 in a Podman container; the schema is dropped before every run
- The machine was not idle — an IDE and other development workloads ran alongside
- "Ready" is wall-clock time from process start until `/actuator/health/readiness` answers, polled
  roughly every 100 ms; RSS is `ps -o rss`; three runs each, the table shows the range

| | JVM | Native |
|---|---|---|
| Build, application only (`clean bootJar` / `clean nativeCompile`), warm Gradle daemon | 2 s | 137 s |
| Build incl. tests (`clean build`, all tests / `nativeCompile` + `nativeTest`) | 82 s | 316 s |
| Peak memory of the build process | not measured | 14.2 GB |
| Artifact | 135 MB jar (+ a JRE) | 407 MB executable |
| Ready, empty database (creates the engine schema, migrates, deploys) | 4.9 – 5.1 s | 0.92 – 0.94 s ¹ |
| Ready, existing database (restart) | 4.5 – 4.6 s | 0.73 – 0.74 s |
| Spring's own "Started in", existing database | 4.1 – 4.3 s | 0.67 – 0.68 s |
| RSS, idle after start | 970 – 1002 MB | 338 – 340 MB |
| RSS after the scenario run | 939 – 970 MB | 404 – 410 MB |
| Bruno scenarios, 45 requests | 26.3 – 26.6 s | 25.1 – 26.1 s |
| `nativeTest`: the 7 process scenarios inside the test image | — | 0.77 s |

¹ The very first launch after each build took 3.3 – 3.5 s — macOS verifying a new 400 MB binary. It
is excluded here.

Reading the numbers:

- **Observed:** the native executable is ready about 6× sooner on an existing database and about 5×
  sooner on an empty one, where creating the engine schema is database work no compiler shortens.
- **Observed:** resident memory is a third of the JVM's when idle and about 43 % after the scenario
  run. The JVM ran with default
  ergonomics on a 64 GB machine; a container memory limit would narrow the gap.
- **Observed:** the scenario run takes the same time on both. It is dominated by polling and by the
  incident demo's real 10-second retry intervals, so it says nothing about throughput.
- **Observed:** the executable is 3× the size of the jar. A fair part of that is dead
  weight the baseline already carries: the CIB seven webclient pulls in JDBC drivers for Oracle, DB2,
  SQL Server, MySQL, MariaDB and H2, and `ServiceLoader` keeps them all reachable.

## 12. Assessment

**Conclusion.** An embedded CIB seven engine *can* run as a native image, including `/engine-rest` and
the webapps, with a small and testable amount of glue: five registrars, one post-processor, one
replacement bean and one build step, no change to models or business code. Start-up and memory improve by the factors one
expects from native images.

Whether it *should* depends on what those factors are worth:

| In favour | Against |
|---|---|
| ready in well under a second on an existing database | unsupported by CIB seven; workarounds tied to its internals |
| a third to a half of the memory | missing hints fail at run time, on the affected path only |
| no JVM in the image | builds take minutes and 15–20 GB of RAM; mock-based tests cannot run natively |
| | no JIT, serial GC only in the Community edition — steady-state throughput unknown |
| | bean conditions and profiles frozen at build time |
| | Linux image not yet verified |

A process engine is a long-running, stateful service: it starts rarely and then works for weeks.
Start-up time is seldom its bottleneck, and the JVM's JIT and collectors are an asset for exactly that
profile. The native build pays off where start-up and idle footprint dominate — scale-to-zero
deployments, many small engines side by side, short-lived preview environments.

## 13. Recommendation

1. **Do not make native the default** of this or the sibling blueprints. Keep the JVM image as the
   reference deployment.
2. **Keeping the opt-in lane is cheap**: without `-Pnative` nothing changes, and the two workarounds
   also harden the JVM build's AOT mode. If it is merged, add a nightly CI job that runs
   `nativeTest` and `scripts/e2e.sh native` on Linux — a lane nobody runs will rot with the next CIB
   seven upgrade.
3. **Before any production use**, in this order: build and run the Linux image; load-test it against
   the JVM image with the same memory limit; click through Cockpit and Tasklist; decide whether the
   Oracle GraalVM licence (G1, PGO) is an option.
4. **Take the findings upstream** — they are small and would make CIB seven AOT-friendly for
   everyone, native or not: precise `@Bean` return types in `CamundaBpmConfiguration` and
   `SevenWebclientContext`; no `Resource.getFile()` in `DefaultDeploymentConfiguration` and
   `SpringTransactionsProcessEngineConfiguration`; reachability metadata for the engine.
5. **For other Miragon blueprints**, native is far more attractive where the engine is *remote*
   (Camunda 8 / Zeebe clients, external-task workers): those services are thin, start often and carry
   none of the engine's reflection. That is where a native lane earns its keep first.
