#!/usr/bin/env python3
"""Select the canonical PostgreSQL groups and exchange only compact test receipts."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import stat
import sys
import xml.etree.ElementTree as ET


DEFAULT_MANIFEST = Path(__file__).with_name("qa_postgres_manifest.json")
CLASS = re.compile(r"[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+", re.ASCII)
SHA = re.compile(r"[a-f0-9]{40}")
DIGEST = re.compile(r"[a-f0-9]{64}")
GROUPS = tuple(f"pg-{number}" for number in range(1, 9))
ZERO = {"skipped": 0, "failures": 0, "errors": 0}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def unique_object(pairs: list[tuple[str, object]]) -> dict:
    value: dict = {}
    for key, item in pairs:
        require(key not in value, "duplicate JSON field")
        value[key] = item
    return value


def read_json(path: Path) -> dict:
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and info.st_size <= 1_048_576, "unsafe JSON file")
    value = json.loads(path.read_bytes(), object_pairs_hook=unique_object)
    require(isinstance(value, dict), "JSON object required")
    return value


def plan(path: Path = DEFAULT_MANIFEST) -> tuple[dict, dict[str, dict[str, int]], str]:
    data = read_json(path)
    require(data.get("schemaVersion") == 1 and data.get("groups") == 8, "invalid group manifest version")
    rows = data.get("classes")
    require(isinstance(rows, list) and len(rows) >= 8, "empty PostgreSQL manifest")
    counts: dict[str, int] = {}
    for row in rows:
        require(isinstance(row, dict) and set(row) == {"className", "tests", "seconds"}, "invalid class entry")
        name, tests, seconds = row["className"], row["tests"], row["seconds"]
        require(isinstance(name, str) and CLASS.fullmatch(name) is not None and name not in counts, "duplicate/invalid PostgreSQL class")
        require(type(tests) is int and tests > 0 and type(seconds) in (int, float)
                and math.isfinite(seconds) and seconds >= 0, "invalid class count/duration")
        counts[name] = tests
    groups: dict[str, dict[str, int]] = {group: {} for group in GROUPS}
    loads = {group: 0.0 for group in GROUPS}
    for row in sorted(rows, key=lambda entry: (-entry["seconds"], entry["className"])):
        group = min(GROUPS, key=lambda name: (loads[name], name))
        groups[group][row["className"]] = row["tests"]
        loads[group] += row["seconds"]
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return counts, groups, digest


def validate_cases(cases: object) -> dict[str, int]:
    require(isinstance(cases, dict) and bool(cases), "empty test inventory")
    require(all(isinstance(name, str) and CLASS.fullmatch(name) is not None
                and type(count) is int and count > 0 for name, count in cases.items()), "invalid test inventory")
    return cases


def validate_selection(cases: dict[str, int], group: str, manifest: Path) -> None:
    postgres, groups, _ = plan(manifest)
    validate_cases(cases)
    if group == "unit":
        require(not any(name.partition("$")[0] in postgres for name in cases), "PostgreSQL class in Java-only group")
    else:
        require(group in groups and cases == groups[group], "PostgreSQL group is incomplete, unexpected or has wrong counts")


def report_inventory(directory: Path) -> dict[str, int]:
    cases: dict[str, int] = {}
    reports = sorted(directory.glob("TEST-*.xml"))
    require(bool(reports), "no Surefire reports")
    for path in reports:
        info = path.lstat()
        require(stat.S_ISREG(info.st_mode) and info.st_size <= 16 * 1024 * 1024, "unsafe Surefire report")
        name = path.name.removeprefix("TEST-").removesuffix(".xml")
        require(CLASS.fullmatch(name) is not None and name not in cases, "invalid/duplicate report class")
        suite = ET.fromstring(path.read_bytes())
        require(suite.tag == "testsuite" and suite.get("name") == name, "report class mismatch")
        totals = {key: int(suite.get(key, "-1")) for key in ("tests", *ZERO)}
        require(totals["tests"] > 0 and all(totals[key] == 0 for key in ZERO), "Surefire report is not green")
        testcases = suite.findall("./testcase")
        require(len(testcases) == totals["tests"] and not any(case.find(tag) is not None
                for case in testcases for tag in ("skipped", "failure", "error")), "Surefire testcase inventory mismatch")
        cases[name] = totals["tests"]
    return cases


def compact_receipt(evidence: Path, group: str, manifest: Path, ci_run: str, ci_attempt: str) -> dict:
    canonical = read_json(evidence)
    require(canonical.get("schemaVersion") == 2 and canonical.get("status") == "passed"
            and canonical.get("suite") == "integration", "canonical evidence has not finalized PASS")
    source = canonical["source"]
    require(source["gitSha"] == source["runStartGitSha"] and source["gitTree"] == source["runStartGitTree"], "source provenance mismatch")
    reports = canonical["reports"]
    cases = {entry["className"]: entry["tests"] for entry in reports}
    require(len(cases) == len(reports), "duplicate canonical report")
    validate_selection(cases, group, manifest)
    require(canonical["totals"] == {"tests": sum(cases.values()), **ZERO}, "canonical totals mismatch")
    return {"schemaVersion": 1, "profile": "VANRIDES_TEST_GROUP_V1", "status": "PASS", "group": group,
            "sourceSha": source["gitSha"], "sourceTree": source["gitTree"],
            "sourceStateSha256": hashlib.sha256(json.dumps(source, sort_keys=True, separators=(",", ":")).encode()).hexdigest(), "clean": source["clean"],
            "manifestSha256": plan(manifest)[2], "ciRunId": ci_run, "ciAttempt": ci_attempt,
            "cases": dict(sorted(cases.items())), "totals": canonical["totals"]}


def aggregate(receipts: list[dict], manifest: Path, sha: str, tree: str, ci_run: str, ci_attempt: str,
              require_clean: bool = False) -> dict:
    require(SHA.fullmatch(sha) is not None and SHA.fullmatch(tree) is not None, "invalid expected source")
    postgres, _, manifest_digest = plan(manifest)
    require(len(receipts) == 9, "exactly eight PostgreSQL groups and one Java-only group required")
    fields = {"schemaVersion", "profile", "status", "group", "sourceSha", "sourceTree", "sourceStateSha256",
              "clean", "manifestSha256", "ciRunId", "ciAttempt", "cases", "totals"}
    seen: set[str] = set()
    union: dict[str, int] = {}
    source_states = set()
    for receipt in receipts:
        require(set(receipt) == fields and receipt["schemaVersion"] == 1
                and receipt["profile"] == "VANRIDES_TEST_GROUP_V1" and receipt["status"] == "PASS", "invalid group receipt")
        group = receipt["group"]
        require(group in (*GROUPS, "unit") and group not in seen, "duplicate or unknown group")
        seen.add(group)
        require(receipt["sourceSha"] == sha and receipt["sourceTree"] == tree
                and receipt["manifestSha256"] == manifest_digest, "source or group manifest mismatch")
        require(receipt["ciRunId"] == ci_run and receipt["ciAttempt"] == ci_attempt, "receipt belongs to another CI execution")
        require(type(receipt["clean"]) is bool and (receipt["clean"] or not require_clean), "dirty source receipt")
        require(isinstance(receipt["sourceStateSha256"], str) and DIGEST.fullmatch(receipt["sourceStateSha256"]) is not None,
                "invalid source state digest")
        source_states.add(receipt["sourceStateSha256"])
        cases = validate_cases(receipt["cases"])
        validate_selection(cases, group, manifest)
        require(receipt["totals"] == {"tests": sum(cases.values()), **ZERO}, "group totals or skips/failures mismatch")
        require(not set(union).intersection(cases), "test class executed by multiple groups")
        union.update(cases)
    require(seen == {*GROUPS, "unit"} and len(source_states) == 1, "missing group or mixed source state")
    require({name: count for name, count in union.items() if name in postgres} == postgres, "incomplete PostgreSQL union")
    return {"status": "PASS", "sourceSha": sha, "sourceTree": tree, "groups": 9,
            "classes": len(union), "tests": sum(union.values()), "postgresClasses": len(postgres),
            "postgresTests": sum(postgres.values()), "javaOnlyTests": sum(count for name, count in union.items() if name not in postgres),
            "cases": dict(sorted(union.items())), **ZERO}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    subparsers = parser.add_subparsers(dest="command", required=True)
    expectations = subparsers.add_parser("expectations")
    expectations.add_argument("--group", choices=("focused", *GROUPS, "unit"), default="focused")
    expectations.add_argument("--reports-dir", type=Path)
    subparsers.add_parser("excludes")
    receipt = subparsers.add_parser("receipt")
    receipt.add_argument("--evidence", type=Path, required=True)
    receipt.add_argument("--group", choices=(*GROUPS, "unit"), required=True)
    receipt.add_argument("--output", type=Path, required=True)
    combined = subparsers.add_parser("aggregate")
    combined.add_argument("--directory", type=Path, required=True)
    combined.add_argument("--expected-sha", required=True)
    combined.add_argument("--expected-tree", required=True)
    combined.add_argument("--ci-run-id", required=True)
    combined.add_argument("--ci-attempt", required=True)
    combined.add_argument("--require-clean", action="store_true")
    combined.add_argument("--baseline", type=Path, help="Optional canonical full-run evidence for exact class/count comparison")
    args = parser.parse_args()
    try:
        postgres, groups, _ = plan(args.manifest)
        if args.command == "expectations":
            if args.reports_dir:
                counts = report_inventory(args.reports_dir)
                validate_selection(counts, args.group, args.manifest)
            else:
                require(args.group != "unit", "Java-only inventory comes from actual Surefire reports")
                counts = postgres if args.group == "focused" else groups[args.group]
            print("\n".join(f"{name}={count}" for name, count in sorted(counts.items())))
        elif args.command == "excludes":
            # Preserve Surefire's default nested-class exclusion and all default include patterns.
            print("**/*$*")
            print("\n".join(name.replace(".", "/") + ".java" for name in sorted(postgres)))
        elif args.command == "receipt":
            value = compact_receipt(args.evidence, args.group, args.manifest,
                                    os.environ.get("GITHUB_RUN_ID", "local"), os.environ.get("GITHUB_RUN_ATTEMPT", "local"))
            with args.output.open("x", encoding="utf-8") as handle:
                os.chmod(args.output, 0o600)
                json.dump(value, handle, sort_keys=True, separators=(",", ":")); handle.write("\n")
        else:
            paths = sorted(args.directory.rglob("*"))
            require(not args.directory.is_symlink() and all(not path.is_symlink() for path in paths), "unsafe receipt directory")
            files = [path for path in paths if path.is_file()]
            require(all(path.suffix == ".json" for path in files), "unexpected artifact file")
            value = aggregate([read_json(path) for path in files], args.manifest, args.expected_sha, args.expected_tree,
                              args.ci_run_id, args.ci_attempt, args.require_clean)
            if args.baseline:
                baseline = read_json(args.baseline)
                inventory = baseline.get("allReports", baseline)["reports"]
                require(value["cases"] == {entry["className"]: entry["tests"] for entry in inventory}, "baseline coverage lost or changed")
            print(json.dumps({key: item for key, item in value.items() if key != "cases"}, sort_keys=True))
        return 0
    except (OSError, ValueError, TypeError, KeyError, ET.ParseError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
