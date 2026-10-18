# `.claude/`

Claude Code tooling for this repo. The guidance itself is in [`AGENTS.md`](../AGENTS.md), which
`CLAUDE.md` imports; this folder holds what only Claude Code uses.

## Skills — `.claude/skills/<name>/SKILL.md`

Workflows, loaded when a task matches their description.

| Skill | When |
|---|---|
| `implement-issue` | take a GitHub issue to a verified change, with a plan approval stop |
| `automate-process` | change an executable BPMN model, wire its glue code, validate it (three nets) |
| `review-change` | review a PR or diff, findings by severity with a failure scenario |
| `create-adr` | record a decision in `docs/adr/` |
| `create-rest-controller` | one endpoint with its `@WebMvcTest`, carried through `openapi.json` to Bruno |
| `create-persistence-adapter` | entity, repository, mapper, adapter, migration and `@DataJpaTest` |
| `create-delegate` | one JavaDelegate or listener with its unit test and the process test of its path |

The layer skills (`create-*`) are small recipes next to the workflows: each points at the existing
classes as the production pattern and states the test pattern, so the agent writes the test first and
matches the code. `implement-issue` and `automate-process` call them.

## Subagents — `.claude/agents/<name>.md`

Read-only reviewers with their own context, loaded at session start.

| Agent | Does |
|---|---|
| `review-process` | checks the models against their glue code, tests and modelling conventions |
| `review-vertical-slice` | walks one feature end to end (BPMN → ports → service → controller or delegate → process test → `openapi.json` → Bruno) |

## Settings and hooks

- `settings.json` denies edits to the generated files (the process API in `adapter/process` and
  `openapi/openapi.json`). The patterns name no variant directory, so they hold in both variants and
  in every starter. It allows the build commands and read-only `gh` commands, and wires one hook.
  Personal additions go in `settings.local.json` (git-ignored).
- `hooks/lint-bpmn.sh` (PostToolUse) runs bpmnlint after every edit of an executable `.bpmn` and
  hands the findings back to Claude. It changes nothing.
- `settings.json` also enables two pinned plugins, `bpmn-cli@miragon-bpmn` (skill `bpmn-cli:bpmn`) and
  `operate@miragon` (skill `operate:operate`, `/operate:status`), from marketplaces fixed to a release
  tag. Claude Code installs them once you trust the folder; cloud sessions do not load them. Its `env`
  sets `BPMN_KEEP_FLOW_IDS=1`, so bpmn-cli keeps flow ids stable. `../.operate.json` points operate at
  the local engine.

## What goes where

| Kind | Place | Example |
|---|---|---|
| orientation, boundaries, conventions | `AGENTS.md` | "Spring Data types stop at the adapter" |
| a repeatable workflow | a skill | implementing an issue |
| an independent read-only review | an agent | model ↔ glue consistency |
| a rule a machine can check | a test, linter or CI step | ArchUnit, bpmnlint, drift checks |
| feedback right after an edit | a hook (small, no side effects) | bpmnlint on `.bpmn` |
| a hard no on a path | a deny rule in `settings.json` | editing generated files |

One fact, one place: a skill links to `AGENTS.md` instead of repeating it. Keep skill descriptions
quoted (an unquoted ` #` starts a YAML comment and cuts them off). A `variant:` marker must stand on a
line of its own, or `scripts/create-starter.sh` leaves it behind. `.claude/logs/` is local and
git-ignored.
