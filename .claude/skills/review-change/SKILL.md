---
name: review-change
argument-hint: "[PR number | branch | commit sha | empty for the working tree against origin/main]"
description: "Review a change in this repo for business correctness, architecture, security and tests, and report findings ranked by severity, each with file:line and a concrete failure scenario. Checks the repo-specific counterparts a change needs (migration, contract, regenerated process API, process tests, Bruno, the other variant) and delegates the model check to the agent review-process. Use when the user asks to review a PR, a branch or the current diff. Read-only; posts nothing unless asked."
allowed-tools: Read, Glob, Grep, Bash(git *), Bash(gh pr view *), Bash(gh pr diff *), Bash(gh pr checks *), Bash(gh run list *), Bash(gh run view *), Bash(gh issue view *), Bash(gh api repos/*/pulls/*/comments *), Agent
---

# Skill: review-change

A review that only ticks boxes is worthless; one finding with a reproducible scenario beats ten
style notes. The machine gates (architecture rules, contract and process-API drift, bpmnlint,
mutation 80) run in CI; do not restate them, check what they cannot see. Do check that a gate
actually ran: the mutation gate is diff-scoped and skips when no main class changed. Generic code
review is what the built-in review tooling does; this skill adds what only this repo knows.

The agent `review-process` is loaded at session start. If it is not available, read
`.claude/agents/review-process.md` and apply it yourself.

## 1. Collect the context

- Diff: `gh pr diff <n>` (open or merged), `git diff origin/main...<branch>`, `git show <sha>` for a
  single commit. For the working tree: `git fetch`, then `git diff $(git merge-base origin/main HEAD)`
  (committed, staged and unstaged) plus `git ls-files --others --exclude-standard` (new files).
- Intent: the PR body and the linked issue (`gh pr view <n> --json closingIssuesReferences`,
  `gh issue view <n>`) with its acceptance criteria. Without an issue, the PR body is the intent.
- `gh pr checks <n>` if it is a PR; `gh run view <id> --log-failed` for a red one. Leave existing
  review comments for step 5, so they don't bias you: `gh pr view <n> --comments` for the
  conversation, `gh api repos/{owner}/{repo}/pulls/<n>/comments --paginate` for inline comments.

## 2. Read the change in full

Read every changed file, not only the hunks, plus the callers and tests of changed code. Ask of each
change: is it what the issue asks for, and nothing else? Does it hold for the process's other paths
(withdrawal at any time, the bike-unavailable error, the signature escalation, compensation of
completed steps, a 409 because the case moved on)? For a move or rename, `git grep` the old paths
and packages instead: a leftover reference is the typical defect.

## 3. Check the counterparts

| The change touches | It also needs |
|---|---|
| a JPA entity | a new `V<n>__*.sql` migration; no edited migration |
| a controller method or a DTO | `@Operation(operationId)` on the method, the re-exported `openapi/openapi.json`, a Bruno request |
| a `.bpmn` | the regenerated `adapter/process`, a process test per new path, Bruno updated for renamed element ids; run the agent `review-process` |
| the DMN or a form | a process test for the decision or the user task; the form `id` still matching the `formRef` |
| a delegate, listener or process adapter | a process test for the path it serves; run the agent `review-process` |
| a service throwing what a delegate maps to `BpmnError` | `noRollbackFor` on `@Transactional` |
| a user-visible REST flow | the Bruno scenario, and the scenario list in `bruno/README.md` |
| the incident demo (`bruno/06-incident-demo`) | still produces its incident |
| an accepted ADR | a reversal is a new ADR that supersedes it; no "Update" sections |

A hand-edited generated file is a blocker.

<!-- variant:blueprint -->
While both variants live side by side, a change in behaviour also needs the same change with
equivalent tests in **both** `kotlin-gradle/` and `java-maven/` (models, forms, migrations and
`application.yaml` byte-identical; the agent `review-process` compares the glue), and stack-specific
content in a shared file needs `variant:` markers on lines of their own, so
`scripts/create-starter.sh` can strip it.
<!-- /variant:blueprint -->

## 4. Security and robustness

Input reaching the domain unvalidated, problem details leaking internals, secrets or credentials in
code or config, new dependencies without a pinned version, process variables carrying personal data
the engine does not need.

## 5. Report

Severity is absolute, not relative to this PR:

- **Blocker:** wrong business result, data loss, broken path, security hole, red gate.
- **Major:** a missing counterpart or test that lets a regression through, or a broken repo rule that
  loses information.
- **Minor:** maintainability, naming, a weak assertion.
- **Nit:** optional.

When unsure between two levels, pick the lower one and say why. Per finding: severity, `file:line`,
what goes wrong in which scenario, a suggested fix. Then: questions for the author, what looks good,
and whether existing review comments are already addressed.

Post to GitHub only when asked, and show the text first.
