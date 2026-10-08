# Create a single-stack starter

The blueprint carries the service twice — Kotlin + Gradle and Java + Maven — so the two can be compared
and kept equivalent. A project built on it needs only one. `scripts/create-starter.sh` removes the other
one and everything that exists only for it.

```bash
git clone git@github.com:miragon-blueprints/cibseven-embedded-example.git my-service
cd my-service

scripts/create-starter.sh kotlin-gradle     # our recommendation for a modern stack
# or
scripts/create-starter.sh java-maven

git status                                  # review, then commit
```

## What the script does

- deletes the other variant's directory and its `pre-merge-*` and `nightly-*` workflows
- strips the other variant's blocks from `README.md`, `AGENTS.md`, `CONTRIBUTING.md`, `shared/README.md`,
  `.github/dependabot.yml`, `.github/workflows/dependency-repair.yml` and `.conductor/settings.toml`
- removes what only makes sense while both variants exist: the stack comparison in the README, the
  "change both variants" rule, ADR-0013, this guide and the script itself
- lists the lines that still mention the removed stack, for a manual look

It works in place, commits nothing and refuses to run on a working tree with uncommitted changes, so
`git restore --staged --worktree .` undoes it.

The directory layout stays as it is (`kotlin-gradle/` or `java-maven/` next to `shared/`). Paths in the
workflows and docs remain valid, and later changes from the blueprint can still be merged.

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

`variant:java-maven` marks the Java + Maven counterpart, and `variant:blueprint` marks content that is
dropped from both starters. When you add stack-specific content to a shared file, wrap it the same way.
