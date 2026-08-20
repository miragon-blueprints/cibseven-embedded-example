# 0008 — Fixed ports for v1, portless as the upgrade path

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

This repo is developed with [Conductor](https://conductor.build), which runs each task in its own git
worktree — potentially several at once. Parallel worktrees that all bind the same ports collide. Two
answers exist: **fixed ports + serialised runs**, or **portless** (stable per-worktree `.localhost`
URLs that avoid collisions). Portless is the nicer end state, but it only wraps what it can slug — a JS
dev server — and this headless stack has nothing for it to wrap: every collision source is a backend
process that binds a real port.

## Decision

For v1 we use **fixed ports and serialise the runs.** `.conductor/settings.toml` sets
**`run_mode = "nonconcurrent"`**, so only one worktree runs the app at a time and the ports never
clash. The headless stack binds just two ports (the same table published in `AGENTS.md` and the README
— see [ADR-0005](0005-agents-md-as-the-single-source.md)):

| Port | Bound by | Serves |
| ---- | -------- | ------ |
| 8080 | Spring Boot app | the REST API, the embedded CIB seven webapp (Cockpit/Tasklist) at `/camunda`, `/engine-rest`, and `/v3/api-docs` |
| 5432 | Postgres | application tables **and** the shared CIB seven engine schema |

**Portless is deferred, deliberately.** Of the collision sources, portless wraps *none*:

| Collision source        | Port | Wrapped by portless? |
| ----------------------- | ---- | -------------------- |
| Spring Boot app         | 8080 | ❌ no |
| Postgres                | 5432 | ❌ no |
| CIB seven engine schema | (shared DB schema in Postgres) | ❌ no |

Because this is a **headless** template, there is no JS dev server for portless to slug. Every
collision source — the Spring port, the Postgres port, and the shared engine schema — is **outside what
portless wraps.** Adopting it now would buy nothing: the backend, database, and engine schema would
still collide, i.e. still force `nonconcurrent`. So it stays deferred until the backend/DB isolation
story is solved.

## Consequences

- **Positive:** dead-simple, predictable URLs; the same ports in dev, tests, CI, and the docs; no
  slug/proxy layer to reason about.
- **Negative / trade-offs:** only one worktree can run the app at once (`nonconcurrent`); truly parallel
  end-to-end runs across worktrees are not possible in v1.
- **Neutral:** per-worktree Postgres/schema isolation is the recorded upgrade path — a future ADR would
  supersede this one once all collision sources are covered.
