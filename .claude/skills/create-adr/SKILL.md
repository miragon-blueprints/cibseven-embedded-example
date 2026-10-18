---
name: create-adr
description: "Write a new Architecture Decision Record under docs/adr/ in this repo's format (Status, Date, Deciders, Context, Decision, Consequences). Use when the user asks to write an ADR, record an architecture decision, or document why something was done. Numbers it after the highest existing record, copies docs/adr/0000-adr-template.md, marks a superseded ADR, adds it to the list in docs/README.md, and shows the full draft before writing."
allowed-tools: Read, Write, Edit, Glob, Grep
---

# Skill: create-adr

Write a new ADR following `docs/adr/` (see
[ADR-0001](../../../docs/adr/0001-record-architecture-decisions.md) and `docs/README.md`).

## Rules

- **A reversal is a new ADR** that supersedes the old one: flip the old one's Status to
  "Superseded by [ADR-NNNN](…)". A refinement of the same decision may rewrite the record in place
  with a bumped Date, as the template allows. Never add "Update" or changelog sections.
- **Record what is decided, not what is planned.** Code proves an implementation, not the motive:
  a reason the user did not state is marked "(⚠ reconstructed, to be confirmed)".
- Numbers have gaps; never reuse or renumber one.
- One decision per record, roughly one screenful, present tense.

## Instructions

1. **Glob `docs/adr/*.md`.** Stop and ask if `0000-adr-template.md` is missing; never invent one.
2. **Next number** = highest four-digit prefix + 1, zero-padded.
3. **Slug** = kebab-cased title. Path: `docs/adr/{NNNN}-{slug}.md`.
4. **Read the template** and keep its sections (Status, Date, Deciders, Context, Decision,
   Consequences, optional Implementation notes last).
5. **Gather content** the request does not already answer: context and options, the decision and its
   mechanics, consequences, status (default `Accepted`).
6. **Draft and cross-link** related ADRs by relative path (hexagon → `0002-…`, contract → `0003-…`,
   mutation gate → `0004-…`, agent instructions → `0005-…`). Show the full draft and ask:
   "Write this to `{path}`? (yes / edit / cancel)".
7. **Write and report.** Add a row to the ADR table in `docs/README.md`. Remind the user to commit the
   ADR with the change it documents.
