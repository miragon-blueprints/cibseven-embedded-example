---
name: create-persistence-adapter
argument-hint: "[Repository port or domain aggregate]"
description: "Create or update the JPA persistence adapter for one domain aggregate: entity, Spring Data repository, mapper, the @Component implementing the outbound Repository port, its Flyway migration and its @DataJpaTest. Use when the user asks to create a persistence adapter, persist an aggregate, add a column or a query, or add an outbound db adapter."
allowed-tools: Read, Write, Edit, Glob, Grep, Bash
---

# Skill: create-persistence-adapter

Files per aggregate in `adapter/outbound/db/` (package `io.miragon.blueprint` under
`service/app/src/main/`), the tests next to them in the test tree. The rules are in `AGENTS.md`
(Architecture, Testing) and [ADR-0010](../../../docs/adr/0010-flyway-for-database-migrations.md).

## Production pattern

Copy the shape of the existing adapters:

| Aggregate | Reference | Shows |
|---|---|---|
| `LeasingApplication` | `LeasingApplicationEntity`, `LeasingApplicationJpaRepository`, `LeasingApplicationEntityMapper`, `LeasingApplicationPersistenceAdapter` | a separate mapper, a filtered and paged query behind the port's own `Criteria`/`Page` |
| `Bike` | `BikeEntity`, `BikePortfolioJpaRepository`, `BikePortfolioPersistenceAdapter` | a small aggregate mapped inside the adapter |

- The adapter is a `@Component` implementing exactly the outbound `*Repository` port.
- **Spring Data types stop at the adapter.** The port owns `Criteria`/`Page`; the adapter translates
  (`PageRequest`, `Sort`) and returns the port's types.
- Columns are `snake_case`, enums `@Enumerated(EnumType.STRING)`, value objects are mapped to
  primitives and back (`ApplicationId(UUID)` ↔ `UUID`).
- **Flyway owns the schema.** Hibernate only validates (`ddl-auto: validate`), so every entity change
  ships with the next `service/app/src/main/resources/db/migration/V<n>__<what>.sql`; never edit an
  applied migration. A new filter or sort column gets an index.

## Test pattern

`@DataJpaTest` + `@ActiveProfiles("test")` + `@Import(<Adapter>)` (reference:
`LeasingApplicationPersistenceAdapterTest`), one test per port method and per filter, including the
empty result:

- **Round trip through a cleared context:** save, `flush()`, `clear()`, then read back, or the test only
  reads the first-level cache.
- **Read tests seed independently** of the adapter under test: an `@Sql` script in
  `service/app/src/test/resources/sql/` (see `leasing-application.sql` and the `findById` test), so a
  broken `save` cannot hide a broken read. The older `findAll` tests and `BikePortfolioPersistenceAdapterTest`
  still seed through `save`; don't copy that.
- **A new column** also goes into the seed script and into the defaults of the domain test factory
  (`TestObjectBuilder`). The test schema is generated from the entities (`ddl-auto: create-drop`), so
  a seed without the column still runs and the round trip silently compares `null`.
- Compare whole objects (`usingRecursiveComparison()`) built with the domain test factory
  (`TestObjectBuilder`). A separate mapper gets its own plain unit test for every field
  (`LeasingApplicationEntityMapperTest`).

Tests run against H2 with Flyway off; the migration is checked when the service starts against
Postgres, so start it once after a schema change.

## Instructions

1. **Resolve the aggregate** from `$ARGUMENTS` (a `*Repository` port in `application/port/outbound/` or
   an aggregate in `domain/`), else list the ports and ask.
2. **Derive the target state:** columns, value-object mapping, the queries the port needs. Add only
   what is missing to existing files.
3. **Write the tests first**, then entity, repository, mapper, adapter and migration.
4. **Verify:** the adapter and mapper tests by name, then the architecture tests (`AGENTS.md` →
   Verify After Each Task); with the stack running, start the service once so Hibernate validates the
   schema against the migrated Postgres.
<!-- variant:blueprint -->
5. **Both variants:** the same classes and tests in `kotlin-gradle/` and `java-maven/`; the migration
   is byte-identical in both.
<!-- /variant:blueprint -->
