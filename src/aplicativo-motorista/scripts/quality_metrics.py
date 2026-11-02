#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


APP_RULES = {
    "file_limit": 600,
    "protected_metrics": [
        "set_state_count",
        "debug_print_count",
        "analyzer_ignore_count",
        "shared_preferences_violation_count",
        "dart_files_over_limit_count",
        "stale_completed_card_count",
        "root_screen_count",
    ],
}

ALLOWED_SHARED_PREFERENCES_PATHS = {
    "lib/app/session/app_session_store.dart",
}


def count_lines(path: Path) -> int:
    with path.open("r", encoding="utf-8", errors="ignore") as handle:
        return sum(1 for _ in handle)


def relative_path(root: Path, path: Path) -> str:
    return path.relative_to(root).as_posix()


def dart_files(root: Path, rel_roots: list[str]) -> list[Path]:
    files: list[Path] = []
    for rel_root in rel_roots:
        base = root / rel_root
        if not base.exists():
            continue
        files.extend(sorted(base.rglob("*.dart")))
    return files


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def count_pattern(files: list[Path], pattern: re.Pattern[str]) -> int:
    return sum(len(pattern.findall(read_text(path))) for path in files)


def matching_paths(root: Path, files: list[Path], pattern: re.Pattern[str]) -> list[str]:
    matches = []
    for path in files:
        if pattern.search(read_text(path)):
            matches.append(relative_path(root, path))
    return sorted(matches)


def line_map(root: Path, files: list[Path]) -> dict[str, int]:
    return {relative_path(root, path): count_lines(path) for path in sorted(files)}


def largest_files(files: dict[str, int], limit: int = 12) -> list[dict[str, Any]]:
    return [
        {"path": path, "lines": lines}
        for path, lines in sorted(files.items(), key=lambda item: (-item[1], item[0]))[
            :limit
        ]
    ]


def file_category(path: str) -> str:
    if path.startswith("integration_test/"):
        return "integration_test"
    if path.startswith("test/"):
        return "widget_test"
    if path.startswith("lib/"):
        return "production"
    return "other"


def files_over_limit(files: dict[str, int], file_limit: int) -> list[dict[str, Any]]:
    return [
        {
            "category": file_category(path),
            "lines": lines,
            "path": path,
        }
        for path, lines in sorted(files.items())
        if int(lines) > file_limit
    ]


def max_file_lines_by_category(files: dict[str, int]) -> dict[str, int]:
    maxima = {
        "production": 0,
        "widget_test": 0,
        "integration_test": 0,
    }
    for path, lines in files.items():
        category = file_category(path)
        if category in maxima:
            maxima[category] = max(maxima[category], int(lines))
    return maxima


def stale_completed_quality_cards(root: Path) -> list[str]:
    backlog = root / "backlog" / "Flutter-Cards.md"
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


def scan(root: Path) -> dict[str, Any]:
    scoped_files = dart_files(root, ["lib", "test", "integration_test"])
    lib_files = dart_files(root, ["lib"])
    files = line_map(root, scoped_files)

    shared_preferences_pattern = re.compile(r"SharedPreferences\.getInstance\s*\(")
    shared_preferences_paths = matching_paths(root, lib_files, shared_preferences_pattern)
    shared_preferences_violations = [
        path
        for path in shared_preferences_paths
        if path not in ALLOWED_SHARED_PREFERENCES_PATHS
    ]

    root_screens = sorted(
        relative_path(root, path) for path in (root / "lib").glob("*screen.dart")
    )
    oversized_files = files_over_limit(files, int(APP_RULES["file_limit"]))
    category_maxima = max_file_lines_by_category(files)
    stale_cards = stale_completed_quality_cards(root)

    metrics = {
        "dart_file_count": len(scoped_files),
        "line_count": sum(files.values()),
        "set_state_count": count_pattern(lib_files, re.compile(r"setState\s*\(")),
        "debug_print_count": count_pattern(
            scoped_files, re.compile(r"\b(?:print|debugPrint)\s*\(")
        ),
        "analyzer_ignore_count": count_pattern(
            scoped_files, re.compile(r"ignore_for_file|//\s*ignore:")
        ),
        "shared_preferences_count": count_pattern(
            lib_files, shared_preferences_pattern
        ),
        "shared_preferences_violation_count": len(shared_preferences_violations),
        "dart_files_over_limit_count": len(oversized_files),
        "production_max_file_lines": category_maxima["production"],
        "widget_test_max_file_lines": category_maxima["widget_test"],
        "integration_test_max_file_lines": category_maxima["integration_test"],
        "stale_completed_card_count": len(stale_cards),
        "root_screen_count": len(root_screens),
    }

    return {
        "version": 1,
        "repo": "vanrides-app",
        "metrics": metrics,
        "files": files,
        "details": {
            "largest_files": largest_files(files),
            "files_over_limit": oversized_files,
            "max_file_lines_by_category": category_maxima,
            "root_screens": root_screens,
            "shared_preferences_violations": shared_preferences_violations,
            "stale_completed_cards": stale_cards,
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


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="VanRides app quality metrics")
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
        failures = evaluate_ratchet(load_json(baseline_path), current, APP_RULES)
        if failures:
            print("Quality ratchet failed:")
            for failure in failures:
                print(f"- {failure}")
            return 1
        print("Quality ratchet PASS")
        return 0

    print(json.dumps(current, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
