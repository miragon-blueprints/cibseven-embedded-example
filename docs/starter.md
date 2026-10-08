# Create a single-stack starter

The blueprint carries the service twice — Kotlin + Gradle and Java + Maven — so the two can be compared
and kept equivalent. A project built on it needs only one. `scripts/create-starter.sh` removes the other
one and everything that exists only for it.

```bash
git clone git@github.com:miragon-blueprints/cibseven-embedded-example.git my-service
cd my-service

scripts/create-starter.sh kotlin-gradle --flat     # our recommendation for a modern stack
# or
scripts/create-starter.sh java-maven --flat

git status                                         # review, then commit
```

## Two layouts

| | `--flat` | without |
|---|---|---|
| **Result** | a plain Spring Boot project: the build in the repo root, the process assets in `service/app/src/main/resources` | the blueprint's layout: `kotlin-gradle/` or `java-maven/` next to `shared/` |
| **Choose it when** | you start your own project from the blueprint | your repo keeps following the blueprint and merges its changes |

`--flat` rewrites paths in the build, the workflows and the docs, so later blueprint changes no longer
merge cleanly. Without it nothing is moved and every path stays valid.

## What the script does

- deletes the other variant's directory and its `pre-merge-*` and `nightly-*` workflows
- strips the other variant's blocks from `README.md`, `AGENTS.md`, `CONTRIBUTING.md`, `shared/README.md`,
  `.github/dependabot.yml`, `.github/workflows/dependency-repair.yml` and `.conductor/settings.toml`
- removes what only makes sense while both variants exist: the stack comparison in the README, the
  "change both variants" rule, ADR-0013, this guide and the script itself
- lists the lines that still mention the removed stack, for a manual look

With `--flat` it additionally

- moves the BPMN and DMN models, the forms and the migrations from `shared/` into
  `service/app/src/main/resources` and points the build at them
- moves the build (wrapper, build files, `service/`) to the repo root and removes the directory prefix
  from the workflows, Dependabot, the Conductor settings and the docs
- appends the variant's README to the root README

It works in place, commits nothing and refuses to run on a working tree with uncommitted changes, so
`git reset --hard` undoes it. Build output of the removed directories is not tracked and stays behind;
delete it by hand.

## What is left to you

- Rename the project: `rootProject.name` / `artifactId`, the image name `miravelo/cibseven-embedded-example`,
  the package `io.miragon.blueprint` and the repository URL in `CONTRIBUTING.md`.
- Update `CODEOWNERS`, and in the repository settings the required status checks, which are named after
  the jobs of the remaining `pre-merge-*` workflow.
- Rewrite the README for your own service.

## How blocks are marked

Content that belongs to one variant is wrapped in marker comments — `<!-- … -->` in Markdown, `# …` in
YAML and TOML:

```
<!-- variant:kotlin-gradle -->
…only in the Kotlin + Gradle starter…
<!-- /variant:kotlin-gradle -->
```

`variant:java-maven` marks the Java + Maven counterpart, `variant:blueprint` marks content that is
dropped from both starters, and `layout:shared` marks content that only applies while the process
assets live in `shared/` (dropped by `--flat`). A marker can carry several tags. When you add
stack-specific content to a shared file, wrap it the same way.

The `Starter Checks` workflow creates and builds both flat starters on every pull request, so a path the
script does not rewrite fails there.
