# Mutation Testing with PIT

> **In one line:** mutation testing checks not whether the tests *run* the code, but whether they
> would actually *notice if the code broke*.

This repo uses [PIT (pitest)](https://pitest.org/) as a **guardrail for test quality** — especially
useful when tests are written or expanded with AI assistance, where high coverage can hide weak
assertions.

---

## Why — coverage is not enough

Line coverage only answers *"was this line executed by a test?"*. That can look green while proving
nothing:

```kotlin
fun isAdult(age: Int): Boolean = age >= 18

@Test
fun testAdult() {
    isAdult(20) // no assertion — the line ran, but nothing was checked
}
```

✅ Line coverage: line executed &nbsp;&nbsp; ❌ Test quality: no one verifies the result.

**Mutation testing** closes that gap. PIT compiles the production code, then injects small faults
("mutants") into the bytecode — e.g. `age >= 18` → `age > 18` — and re-runs the covering tests:

```
production code → PIT injects a small fault → tests run against the mutant
   ├── a test fails → mutant KILLED   ✅  (a test guards this behaviour)
   └── all tests green → mutant SURVIVED ⚠️  (the tests are too weak, or missing)
```

| Result | Meaning | Interpretation |
| --- | --- | --- |
| **Killed** | a test caught the change | good — behaviour is guarded |
| **Survived** | the change went unnoticed | warning — the test is too weak or asserts too little |
| **No coverage** | no test exercised the line | stronger warning — the line isn't tested at all |

The headline metric is the **mutation score** = `killed / total mutations`. A high score means the
tests catch typical faults; a high *coverage* with a low *mutation score* means the tests look good
but check too little.

---

## How to run

```bash
./gradlew :service:app:pitest
```

- **HTML report:** `service/app/build/reports/pitest/index.html` — it combines **line coverage and
  mutation coverage** per class and highlights each surviving mutant inline in the source.
- **Machine-readable:** `service/app/build/reports/pitest/mutations.xml` (consumed by CI).
- The task fails if the mutation score drops below the configured **threshold** (regression gate).
- **CI:** a dedicated **`mutation` job** in [`.github/workflows/pre-merge.yml`](../.github/workflows/pre-merge.yml)
  runs `:service:app:pitest`, fails under the threshold, and uploads the HTML report as an artifact.

### Configuration

Configured in [`service/app/build.gradle.kts`](../service/app/build.gradle.kts) (`pitest { … }`):

- **Mutated:** the whole `io.miragon.blueprint.*` application module.
- **Excluded from mutation** (would only add noise): the bpmn-to-code *generated* process API, the
  Spring bootstrap class, and the `adapter.inbound.cibseven.*` BPMN delegates/listeners (thin glue
  exercised only by the process tests below).
- **Kill-set:** fast mockk/`@WebMvcTest`/`@DataJpaTest` unit tests. The **engine + JGiven integration
  tests** (`process.*`) and the **ArchUnit/Konsist** structural tests (`architecture.*`) are excluded
  from the kill-set — they would make every run slow and non-deterministic without adding mutation
  signal.
- **Threshold:** `mutationThreshold = 80` (see [the Kotlin caveat](#the-kotlin-caveat-important) for why not 100).

---

## Reading a surviving mutant — worked examples from this repo

The spike found (and fixed) two textbook weak-test patterns:

**1. A test that asserts too few fields.** `GetLeasingApplicationControllerTest` returned an 8-field
DTO but only asserted 3 fields, so PIT could blank out `customerName`, `email`, `bikeId`, `orderId`
and `contractId` (return `""`) and every test stayed green. Fix: assert **all** mapped fields, using
an application that actually has an order and a contract.

**2. A test that only covers one branch of a boolean.** `RequestOrderCancellationServiceTest` only
checked the `true` path, so the mutant *"always return true"* was **equivalent** to the original for
that test. Fix: add the `false` path and assert `isFalse()`. This is the canonical mutation-testing
lesson — one assertion per outcome is not enough; you need both outcomes.

---

## The Kotlin caveat (important)

PIT mutates JVM **bytecode**, and the Kotlin compiler emits synthetic constructs — `require`/null
checks in `value class` constructors, safe-call (`?.`) mapping, `data class` accessors — that PIT
faithfully mutates but **no test can kill**, because the mutated and original bytecode behave
identically. These are *equivalent mutants*: false "survivors".

We proved this during the spike: the `GetLeasingApplicationController.toDto()` safe-call conditionals
**survived even after** both the null and non-null `orderId`/`contractId` paths were covered and
asserted — a strong signal they are equivalent, not a real gap. All ~23 residual survivors are of
this kind (value-class null-checks, nullable-field mapping, trivial `getX()` accessors, defensive
`?: error(...)` guards). That is why **100% is not reachable here** and the threshold sits at 80.

---

## Why this matters for AI-driven development

AI assistants generate tests fast — but *many tests ≠ good tests*, and *high coverage ≠ real safety*.
The well-documented failure mode is **weak assertions**: recent studies find AI-generated suites
often chase coverage while "skipping meaningful assertions, often relying on weak assertions such as
checking for non-null while failing to verify a specific value"
([augmentcode.com](https://www.augmentcode.com/guides/mutation-testing-ai-generated-code)), with
incorrect assertions a leading cause of AI test failures
([ScienceDirect](https://www.sciencedirect.com/science/article/abs/pii/S0950584924000739)).

Mutation testing is the natural counter-measure, because the mutation score measures exactly what
coverage misses — assertion strength — and gives a concrete, per-mutant feedback signal:

```
AI generates tests → tests are green → PIT injects faults
        └── mutants survive? → yes → the AI must strengthen the tests (not add more)
```

This loop is now used in industry: Meta ran LLM-based, mutation-guided test generation across its
apps in late 2024 with engineers accepting 73% of generated tests
([arxiv 2501.12862](https://arxiv.org/pdf/2501.12862)). The takeaway for this repo: use the mutation
score as the acceptance bar for AI-written tests — it turns *"write more tests"* into *"write tests
that would actually catch a bug"*.

**Practical guidance**
- Mutation testing does not replace unit tests — it grades their strength.
- It is slower than normal tests; run it in CI / nightly / for critical modules, not on every save.
- A surviving mutant is not automatically a bug — it's a prompt for review (and, here, sometimes just
  Kotlin equivalent-mutant noise).

---

## Upgrade path: arcmutate Kotlin plugin (optional, commercial)

The residual survivors here are Kotlin equivalent mutants. The commercial
[arcmutate Kotlin plugin](https://docs.arcmutate.com/docs/kotlin.html) removes most of that noise and
would let the threshold rise:

- `+KOTLIN_NO_NULLS` drops mutations to compiler-generated (and hand-rolled) null checks.
- Filters equivalent mutations for Kotlin-specific return types.
- Adds a few Kotlin-aware mutators for constructs that differ from their Java bytecode.

It needs a commercial licence. Without it, open-source PIT + the exclusions above is already a useful
guardrail — the score is just capped a few points below 100 by unavoidable Kotlin noise.

---

> **Coverage asks:** "Was the code touched?"
> **Mutation testing asks:** "Would the test notice if the code were broken?"
