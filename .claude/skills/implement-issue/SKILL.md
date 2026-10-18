---
name: implement-issue
argument-hint: "<issue number or URL>"
description: "Take one GitHub issue of this repo from its acceptance criteria to a verified change: read the issue, mark assumptions, plan along the chain (model, process API, domain and application, delegates, REST and persistence, contract, Bruno), stop for the user's approval of the plan, implement test-first, verify and report each acceptance criterion with its evidence. Use when the user says implement, build or work on issue #n. Never commits, pushes, opens a PR or changes the issue unless asked."
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, Skill, Agent
---

# Skill: implement-issue

The human owns the requirement and the plan; the agent owns the legwork. Two stops are mandatory:
the **plan approval** (step 3) and any **business decision** the issue leaves open.

## 1. Read the issue

```bash
gh issue view <n>
gh issue view <n> --json comments --jq '.comments[].body'
```

Collect the acceptance criteria, the elements of the models it touches and linked issues. An open
issue this one depends on blocks the work: say so and stop.

## 2. Separate facts from assumptions

List every detail the plan needs. Each one is **derived** (cite the issue, the model or the code) or
an **assumption**. Business assumptions (deadlines, gateway conditions, error paths, who works a user
task) become questions, not choices.

## 3. Plan, then stop

If the issue changes a model, run steps 1–2 of skill `automate-process` now (review, restate the
change, collect the business questions); its editing steps come after the approval.

Write the plan as a checklist along the chain, skipping hops the issue does not touch:

1. Model change (skill `automate-process`) and the regenerated process API
2. Domain and application: value objects, ports, services, domain rules
3. Delegates and listeners with their unit tests and a process test per new path (skill
   `create-delegate`)
4. REST and persistence (skills `create-rest-controller`, `create-persistence-adapter`), including the
   re-exported `openapi/openapi.json` and the Flyway migration
5. A Bruno scenario for every path a client drives over REST
6. Docs: `bruno/README.md`, `docs/assets/bike-leasing.png` if the picture changed, an ADR if a decision
   is taken (skill `create-adr`)
<!-- variant:blueprint -->

Every behaviour item lands in **both** variants with equivalent tests; models, forms, migrations and
`application.yaml` are copied byte-identically.
<!-- /variant:blueprint -->

Per item: the files, the test that proves it, the acceptance criterion it serves. Show the plan with
the open questions and **wait for approval**. Do not write code before it.

## 4. Implement test-first

One item at a time: failing test, code, green test, the targeted check from "Verify After Each Task"
in `AGENTS.md`. When the plan turns out wrong, stop and say what changed instead of drifting.

## 5. Verify

Run the checks of "Before opening a PR" in `CONTRIBUTING.md` for every variant you touched, plus
`npm run lint:bpmn` if a model changed. A targeted run proves nothing about tests you did not touch.
If the stack runs, drive the new path with Bruno and diagnose a stuck case with
`npx operate inspect --business-key <applicationId>`. Then let the agent `review-vertical-slice` walk
the feature and run skill `review-change` on the working tree (it covers staged and new files); fix
what they report. An agent that is not loaded in the session is applied by reading its file in
`.claude/agents/`.

## 6. Report

| Acceptance criterion | Evidence (test, check, Bruno run) | Status |
|---|---|---|

Then: files changed, checks run with their result, assumptions made, what was not verified and why.

## Only when asked

- **Commit:** stage files by name (never `git add -A`), Conventional Commit, `Closes #<n>` in the
  body. Never stage `.context/` or local settings.
- **PR:** `gh pr create --base main`, title = the commit subject, body: what, why, how it was verified,
  `Closes #<n>`.
- **Issue:** comment or change labels only on request.
