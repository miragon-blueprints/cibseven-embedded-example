import csv
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ASSERTION_TYPES = ("AssertionError", "AssertionFailedError", "MultipleFailuresError")
CONTEXT_FAILURE_MARKERS = ("Failed to load ApplicationContext", "ApplicationContext failure threshold")
DRIVER_MARKERS = ("no async-continuation job found", "no timer job found")
CASCADE_MARKERS = ("Query return",)


def failures_of(result_dir):
    for report in sorted(result_dir.glob("TEST-*.xml")):
        for testcase in ET.parse(report).getroot().iter("testcase"):
            for problem in list(testcase.findall("failure")) + list(testcase.findall("error")):
                yield testcase.get("classname").split(".")[-1] + "." + testcase.get("name"), problem


def classify_failure(problem):
    kind = problem.get("type") or ""
    detail = problem.get("message") or ""
    if any(marker in detail for marker in CASCADE_MARKERS):
        return "CASCADE_FROM_EARLIER_TEST"
    if any(marker in detail for marker in CONTEXT_FAILURE_MARKERS):
        return "INVALID_MODEL"
    if any(marker in detail for marker in DRIVER_MARKERS) or kind.endswith("NullPointerException"):
        return "KILLED_TEST_DRIVER"
    if kind.endswith(ASSERTION_TYPES):
        return "KILLED_ASSERTION"
    return "KILLED_RUNTIME_ERROR"


def classify_mutant(result_dir):
    exit_code = int((result_dir / "exit-code").read_text().strip())
    failures = list(failures_of(result_dir))
    if exit_code == 124:
        return "TIMEOUT", failures
    if not failures:
        return ("SURVIVED" if exit_code == 0 else "TECHNICAL_ERROR"), failures
    categories = {classify_failure(problem) for _, problem in failures}
    for category in ("INVALID_MODEL", "KILLED_ASSERTION", "KILLED_RUNTIME_ERROR", "KILLED_TEST_DRIVER"):
        if category in categories:
            return category, failures


def short_message(problem):
    return ((problem.get("type") or "").split(".")[-1] + ": " + (problem.get("message") or "")).replace("\n", " ")[:160]


def main(results_dir):
    control_category, _ = classify_mutant(results_dir / "CONTROL")
    print(f"CONTROL: {control_category} in {(results_dir / 'CONTROL' / 'seconds').read_text().strip()}s")
    rows = []
    with open(results_dir / "manifest.csv") as manifest:
        for mutant in csv.DictReader(manifest, delimiter=";"):
            result_dir = results_dir / mutant["id"]
            if not (result_dir / "exit-code").exists():
                continue
            category, failures = classify_mutant(result_dir)
            lint_exit = result_dir / "bpmnlint-exit-code"
            rows.append({
                **mutant,
                "category": category,
                "failing_tests": len(failures),
                "failure_kinds": " ".join(f"{kind}:{count}" for kind, count in sorted(Counter(classify_failure(problem) for _, problem in failures).items())),
                "seconds": (result_dir / "seconds").read_text().strip(),
                "bpmnlint": "n/a" if not lint_exit.exists() else ("clean" if lint_exit.read_text().strip() == "0" else "findings"),
                "first_failure": short_message(failures[0][1]) if failures else "",
                "killing_tests": " ".join(sorted({name for name, _ in failures})),
            })
    with open(results_dir / "results.csv", "w", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=list(rows[0].keys()), delimiter=";")
        writer.writeheader()
        writer.writerows(rows)
    print(Counter(row["category"] for row in rows))
    print(f"{len(rows)} mutants, results in {results_dir / 'results.csv'}")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
