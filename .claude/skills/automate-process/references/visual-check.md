# Validating a model: three nets

BPMNDI defects are invisible in the XML and in a diff: a task can execute but never render, a flow
can run backwards. Run all three nets and report all of them. Paths are relative to the directory
that holds `service/`.

## 1. bpmnlint

```bash
npm run lint:bpmn
```

Geometry and wiring, exact and fast. Expected: clean. Missing bpmnlint → `npm ci` once.

## 2. bpmn-cli validation and layout metrics

```bash
npx bpmn validate service/app/src/main/resources/bpmn/<name>.bpmn
npx bpmn metrics service/app/src/main/resources/bpmn/<name>.bpmn
```

The baseline a change must not worsen: `bike-leasing.bpmn` validates with 3 warnings (the named
join `gateway_rejectionJoin`, the unnamed default branch of `gateway_isSolvent`, the two tasks named
"Send cancellation confirmation") and has 3 layout problems around `event_contractNotSigned` and
`gateway_rejectionJoin`;
`cancel-bike-order.bpmn` has none.

## 3. The rendered image

```bash
npx bpmn render service/app/src/main/resources/bpmn/<name>.bpmn --problems -o .context/verify/<name>.png
```

`--problems` numbers the layout problems in the picture. A model that does not render is a finding.
Read the PNG, not `<dc:Bounds>`, and check in order:

- **Visibility:** every element drawn; a flow ending in empty space means a missing shape. Expected
  off the main flow: the event subprocess *Application withdrawn*, the compensation handlers with their
  associations, and the non-interrupting reminder *7 days*.
- **Reading direction by meaning:** the flow reads left to right. A flow pointing backwards claims
  "the process steps back"; that is right for a loop, wrong for a merge gateway parked left of what it
  feeds.
- **No overlaps, no crossings:** shapes, labels and flows don't cover each other; flows don't run
  through shapes.
- **Labels:** legible, next to their element, and a gateway's branch labels match the
  `conditionExpression` of the flow they sit on.
- **Docking:** flows start and end on the shape border, no zero-length segments.

Crop a detail with `npx bpmn render … <ids>` or `--around <id>` instead of judging a full-width render.

## Report

Overall verdict, then **bpmnlint**, **bpmn-cli**, **image**: each clean or a table of findings with the
element or flow id and the rule it breaks. When fixing, move shape, waypoints and label together, then
rerun all three nets. A defect the image shows but no linter caught is worth a note: it is a missing
lint rule. Delete `.context/verify/` afterwards. If the picture of `bike-leasing.bpmn` changed, refresh
`docs/assets/bike-leasing.png` (the README shows it), e.g. with
`npx --yes bpmn-to-image@0.10.0 --scale 2 <path-to-bpmn>:docs/assets/bike-leasing.png`.
