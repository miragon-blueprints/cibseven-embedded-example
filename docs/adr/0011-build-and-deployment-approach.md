# 0014 — Build and deployment approach: OCI image

- **Status:** Accepted
- **Date:** 2026-08-20

## Context

`stack/docker-compose.yml` starts **only Postgres**. There was no artifact for the app itself, so the
"build & deployment" dimension every template in this family names was empty: a fork could run the dev
loop (`bootRun`) but had no answer to *"how do I ship this as a container?"*. The template aims to be
production-shaped ([ADR-0008](0008-track-the-latest-major-versions.md),
[ADR-0009](0009-actuator-probes-and-prometheus-metrics.md),
[ADR-0010](0010-flyway-for-database-migrations.md)), so it should hand a fork a runnable image, not
just a database.

The backend is a Spring Boot 4 app. Spring's Gradle plugin can build an OCI image directly from the fat
jar with Cloud Native Buildpacks — no Dockerfile to write or keep in sync with the JDK.

## Decision

We produce an **OCI image for the backend with Spring's `bootBuildImage`** (buildpacks, no Dockerfile).

- **Backend image** — `./gradlew :service:app:bootBuildImage` builds
  `miravelo/cibseven-embedded-example:<version>` (`bootBuildImage.imageName` in
  `service/app/build.gradle.kts`, JVM pinned via `BP_JVM_VERSION=21`). Buildpacks give a layered,
  non-root image with no Dockerfile to maintain. A hand-written Dockerfile would only be justified if we
  needed control buildpacks can't give; we don't.
- **The image is self-contained.** Because this is a headless template, the app serves everything on a
  single port — the REST API, the embedded CIB seven webapp (Cockpit/Tasklist), `/engine-rest`, and
  `/v3/api-docs` — so there is no separate web tier to package or reverse-proxy.
- **Postgres stays in `stack/docker-compose.yml`** for the local dev loop. Because Flyway owns the
  schema and Hibernate only validates ([ADR-0010](0010-flyway-for-database-migrations.md)), the named
  volume persists across restarts with no `ddl-auto` override.
- **Config is environment-overridable** (12-factor): `application.yaml` keeps dev defaults so local
  runs are unchanged, but every deploy-relevant value (datasource URL/credentials) is read from an env
  var that wins over the baked default.

The how-to (build the image, the podman socket note) is in
[CONTRIBUTING.md](../../CONTRIBUTING.md).

## Consequences

- **Positive:** `bootBuildImage` produces a runnable container — API, engine, and web UI in one image —
  with no Dockerfile to maintain. The build & deployment dimension is now filled.
- **Negative / trade-offs:** with **podman** the buildpack step needs a Docker-API socket
  (`podman system service` + `DOCKER_HOST`). The image is **not production-hardened** — it carries the
  dev `jwtSecret` and admin/admin credentials from `application.yaml`, which a real deployment must
  override.
- **Neutral:** a CI job that builds the image is a natural follow-up, deferred for now.
