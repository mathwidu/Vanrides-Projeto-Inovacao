#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


BACKEND_RULES = {
    "file_limit": 350,
    "protected_metrics": [
        "system_print_count",
        "mockbean_count",
        "org_json_dependency_count",
        "stale_completed_card_count",
    ],
}


def count_lines(path: Path) -> int:
    with path.open("r", encoding="utf-8", errors="ignore") as handle:
        return sum(1 for _ in handle)


def relative_path(root: Path, path: Path) -> str:
    return path.relative_to(root).as_posix()


def java_files(root: Path, rel_roots: list[str]) -> list[Path]:
    files: list[Path] = []
    for rel_root in rel_roots:
        base = root / rel_root
        if not base.exists():
            continue
        files.extend(sorted(base.rglob("*.java")))
    return files


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def count_pattern(files: list[Path], pattern: re.Pattern[str]) -> int:
    return sum(len(pattern.findall(read_text(path))) for path in files)


def count_pattern_by_file(
    root: Path,
    files: list[Path],
    pattern: re.Pattern[str],
) -> list[dict[str, Any]]:
    matches: list[dict[str, Any]] = []
    for path in sorted(files):
        count = len(pattern.findall(read_text(path)))
        if count == 0:
            continue
        matches.append({"path": relative_path(root, path), "count": count})
    return matches


def line_map(root: Path, files: list[Path]) -> dict[str, int]:
    return {relative_path(root, path): count_lines(path) for path in sorted(files)}


def largest_files(files: dict[str, int], limit: int = 12) -> list[dict[str, Any]]:
    return [
        {"path": path, "lines": lines}
        for path, lines in sorted(files.items(), key=lambda item: (-item[1], item[0]))[
            :limit
        ]
    ]


def productive_files_over_limit(
    root: Path,
    files: list[Path],
    file_limit: int,
) -> list[dict[str, Any]]:
    return [
        {
            "path": relative_path(root, path),
            "lines": count_lines(path),
        }
        for path in sorted(files)
        if count_lines(path) > file_limit
    ]


def stale_completed_quality_cards(root: Path) -> list[str]:
    backlog = root / "backlog" / "SpringBoot-Cards.md"
    if not backlog.exists():
        return []

    text = read_text(backlog)
    cards: list[str] = []
    blocks = re.split(r"(?m)(?=^## Backlog )", text)
    for block in blocks:
        quality_match = re.search(r"\b(QUALITY-\d{4})\b", block)
        if not quality_match:
            continue
        if not re.search(r"(?m)^### Status\s*\nIN_PROGRESS\s*$", block):
            continue
        if not re.search(r"Card marcado como [`']?DONE \(commit:", block):
            continue
        cards.append(quality_match.group(1))
    return sorted(set(cards))


def testcontainers_skipped_reports(root: Path) -> list[dict[str, Any]]:
    reports: list[dict[str, Any]] = []
    for report in sorted((root / "target" / "surefire-reports").glob("TEST-*.xml")):
        text = read_text(report)
        skipped = sum(int(value) for value in re.findall(r'\bskipped="(\d+)"', text))
        if skipped > 0:
            reports.append(
                {
                    "path": relative_path(root, report),
                    "skipped": skipped,
                }
            )
    return reports


def scan(root: Path) -> dict[str, Any]:
    main_files = java_files(root, ["src/main/java"])
    test_files = java_files(root, ["src/test/java"])
    scoped_files = main_files + test_files
    files = line_map(root, scoped_files)
    pom_text = read_text(root / "pom.xml") if (root / "pom.xml").exists() else ""
    migrations = sorted((root / "src/main/resources/db/migration").glob("*.sql"))
    oversized_productive_files = productive_files_over_limit(
        root,
        main_files,
        int(BACKEND_RULES["file_limit"]),
    )
    stale_cards = stale_completed_quality_cards(root)
    skipped_reports = testcontainers_skipped_reports(root)

    metrics = {
        "java_file_count": len(scoped_files),
        "main_line_count": sum(count_lines(path) for path in main_files),
        "test_line_count": sum(count_lines(path) for path in test_files),
        "line_count": sum(files.values()),
        "system_print_count": count_pattern(
            scoped_files, re.compile(r"System\.(?:out|err)\.|printStackTrace\s*\(")
        ),
        "mockbean_count": count_pattern(test_files, re.compile(r"@MockBean\b")),
        "migration_count": len(migrations),
        "org_json_dependency_count": len(
            re.findall(r"<groupId>\s*org\.json\s*</groupId>", pom_text)
        ),
        "productive_java_files_over_limit_count": len(oversized_productive_files),
        "stale_completed_card_count": len(stale_cards),
        "testcontainers_skipped_count": sum(
            int(report["skipped"]) for report in skipped_reports
        ),
    }
    mockbean_files = count_pattern_by_file(
        root,
        test_files,
        re.compile(r"@MockBean\b"),
    )

    return {
        "version": 1,
        "repo": "vanrides-backend",
        "metrics": metrics,
        "files": files,
        "details": {
            "largest_files": largest_files(files),
            "migrations": [relative_path(root, path) for path in migrations],
            "mockbean_files": mockbean_files,
            "productive_java_files_over_limit": oversized_productive_files,
            "stale_completed_cards": stale_cards,
            "testcontainers_skipped_reports": skipped_reports,
        },
    }


def evaluate_ratchet(
    baseline: dict[str, Any],
    current: dict[str, Any],
    rules: dict[str, Any],
) -> list[str]:
    failures: list[str] = []

    baseline_metrics = baseline.get("metrics", {})
    current_metrics = current.get("metrics", {})
    for metric in rules["protected_metrics"]:
        before = int(baseline_metrics.get(metric, 0))
        now = int(current_metrics.get(metric, 0))
        if now > before:
            failures.append(f"{metric} increased from {before} to {now}")

    file_limit = int(rules["file_limit"])
    baseline_files = baseline.get("files", {})
    current_files = current.get("files", {})
    for path, lines in sorted(current_files.items()):
        before = baseline_files.get(path)
        allowed = file_limit if before is None else max(file_limit, int(before))
        if int(lines) > allowed:
            source = "new file" if before is None else "existing file"
            failures.append(
                f"{source} {path} has {lines} lines; allowed maximum is {allowed}"
            )

    return failures


def evaluate_warnings(current: dict[str, Any]) -> list[str]:
    warnings: list[str] = []

    mockbean_count = int(current.get("metrics", {}).get("mockbean_count", 0))
    if mockbean_count > 0:
        files = current.get("details", {}).get("mockbean_files", [])
        file_count = len(files) if isinstance(files, list) else 0
        warnings.append(
            f"mockbean_count remains {mockbean_count} across {file_count} test files; "
            "migrate with @MockitoBean in dedicated batches"
        )

    return warnings


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="VanRides backend quality metrics")
    parser.add_argument("--root", default=".", help="Repository root")
    parser.add_argument("--write-baseline", help="Write current metrics to this file")
    parser.add_argument("--baseline", default="quality/ratchet-baseline.json")
    parser.add_argument("--check", action="store_true", help="Check current metrics")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    current = scan(root)

    if args.write_baseline:
        write_json(root / args.write_baseline, current)
        print(f"Wrote quality baseline: {args.write_baseline}")
        return 0

    if args.check:
        baseline_path = root / args.baseline
        if not baseline_path.exists():
            print(f"Quality baseline not found: {baseline_path}", file=sys.stderr)
            return 2
        failures = evaluate_ratchet(load_json(baseline_path), current, BACKEND_RULES)
        if failures:
            print("Quality ratchet failed:")
            for failure in failures:
                print(f"- {failure}")
            return 1
        warnings = evaluate_warnings(current)
        if warnings:
            print("Quality ratchet warnings:")
            for warning in warnings:
                print(f"- {warning}")
        print("Quality ratchet PASS")
        return 0

    print(json.dumps(current, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
