#!/usr/bin/env python3
"""Deterministic loopback load probe for the VanRides sensitive-route limiter."""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass


LOOPBACK_HOSTS = {"127.0.0.1", "::1", "localhost"}
MAX_RESPONSE_BYTES = 16_384
MAX_REQUESTS = 10_000
MAX_WORKERS = 64
MAX_BODY_BYTES = 1_048_576


@dataclass(frozen=True)
class ProbeResult:
    status: int
    retry_after: str | None
    request_id: str | None
    allow_origin: str | None
    body: bytes


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Exercise a loopback sensitive route and assert exact 429 counts."
    )
    parser.add_argument("--url-template", required=True)
    parser.add_argument("--method", choices=("GET", "POST"), required=True)
    parser.add_argument("--requests", type=int, required=True)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--expected-limited", type=int, required=True)
    parser.add_argument("--expected-non-limited", type=int, required=True)
    parser.add_argument(
        "--expected-non-limited-status",
        type=int,
        action="append",
        required=True,
        help="Allowed status for admitted requests; repeat for an explicit allowlist.",
    )
    parser.add_argument(
        "--expected-non-limited-code",
        action="append",
        default=[],
        help="Optional allowed JSON code for admitted requests; repeat as needed.",
    )
    parser.add_argument("--body", default="")
    parser.add_argument("--content-type", default="application/json")
    parser.add_argument("--origin", default="")
    parser.add_argument("--rotate-x-forwarded-for", action="store_true")
    parser.add_argument("--timeout-seconds", type=float, default=5.0)
    return parser.parse_args(argv)


def validate_args(args: argparse.Namespace) -> None:
    if not 1 <= args.requests <= MAX_REQUESTS:
        raise ValueError("requests must be between 1 and 10000")
    if not 1 <= args.workers <= MAX_WORKERS:
        raise ValueError("workers must be between 1 and 64")
    if len(args.body.encode("utf-8")) > MAX_BODY_BYTES:
        raise ValueError("body must not exceed 1 MiB")
    if args.expected_limited < 0 or args.expected_non_limited < 0:
        raise ValueError("expected counts must not be negative")
    if args.expected_limited + args.expected_non_limited != args.requests:
        raise ValueError("expected counts must equal requests")
    if any(status < 100 or status > 599 or status == 429
           for status in args.expected_non_limited_status):
        raise ValueError("expected non-limited statuses must be HTTP statuses other than 429")
    if any(not code or not code.isascii() for code in args.expected_non_limited_code):
        raise ValueError("expected non-limited codes must be non-empty ASCII values")
    if not 0 < args.timeout_seconds <= 30:
        raise ValueError("timeout must be between 0 and 30 seconds")

    for index in (0, args.requests - 1):
        try:
            url = args.url_template.format(index=index)
        except (IndexError, KeyError, ValueError) as exception:
            raise ValueError("url template may use only the {index} field") from exception
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "http" or parsed.hostname not in LOOPBACK_HOSTS:
            raise ValueError("probe URL must use HTTP loopback")
        if parsed.username or parsed.password or parsed.fragment:
            raise ValueError("credentials and fragments are forbidden in probe URL")
        if parsed.port is None:
            raise ValueError("probe URL must include an explicit port")


def probe_once(args: argparse.Namespace, index: int) -> ProbeResult:
    url = args.url_template.format(index=index)
    headers = {
        "Accept": "application/json",
        "X-Request-Id": f"rate-limit-load-{index}",
    }
    data = None
    if args.method == "POST":
        data = args.body.encode("utf-8")
        headers["Content-Type"] = args.content_type
    if args.origin:
        headers["Origin"] = args.origin
    if args.rotate_x_forwarded_for:
        third = (index // 250) % 250
        fourth = (index % 250) + 1
        headers["X-Forwarded-For"] = f"198.51.{third}.{fourth}"

    request = urllib.request.Request(url, data=data, headers=headers, method=args.method)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(request, timeout=args.timeout_seconds) as response:
            return ProbeResult(
                response.status,
                response.headers.get("Retry-After"),
                response.headers.get("X-Request-Id"),
                response.headers.get("Access-Control-Allow-Origin"),
                response.read(MAX_RESPONSE_BYTES),
            )
    except urllib.error.HTTPError as error:
        return ProbeResult(
            error.code,
            error.headers.get("Retry-After"),
            error.headers.get("X-Request-Id"),
            error.headers.get("Access-Control-Allow-Origin"),
            error.read(MAX_RESPONSE_BYTES),
        )


def validate_limited_response(result: ProbeResult) -> None:
    if result.retry_after is None or not result.retry_after.isdigit():
        raise AssertionError("429 response is missing a numeric Retry-After")
    if int(result.retry_after) < 1:
        raise AssertionError("429 Retry-After must be positive")
    try:
        payload = json.loads(result.body)
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise AssertionError("429 response is not valid JSON") from exception
    if payload.get("code") != "RATE_LIMITED":
        raise AssertionError("429 response does not carry RATE_LIMITED")


def validate_non_limited_response(result: ProbeResult, args: argparse.Namespace) -> None:
    if result.status not in set(args.expected_non_limited_status):
        raise AssertionError(
            "unexpected non-limited status: "
            f"allowed={sorted(set(args.expected_non_limited_status))} actual={result.status}"
        )
    if not args.expected_non_limited_code:
        return
    try:
        payload = json.loads(result.body)
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise AssertionError("non-limited response is not valid JSON") from exception
    if payload.get("code") not in set(args.expected_non_limited_code):
        raise AssertionError("non-limited response carries an unexpected code")


def run(args: argparse.Namespace) -> tuple[int, int]:
    validate_args(args)
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = [executor.submit(probe_once, args, index) for index in range(args.requests)]
        results = [future.result() for future in futures]

    limited = [result for result in results if result.status == 429]
    non_limited = [result for result in results if result.status != 429]
    for index, result in enumerate(results):
        if result.request_id != f"rate-limit-load-{index}":
            raise AssertionError("response does not preserve X-Request-Id")
        if args.origin and result.allow_origin != args.origin:
            raise AssertionError("response does not preserve the CORS origin")
    for result in limited:
        validate_limited_response(result)
    for result in non_limited:
        validate_non_limited_response(result, args)
    if len(limited) != args.expected_limited:
        raise AssertionError(
            f"unexpected limited count: expected={args.expected_limited} actual={len(limited)}"
        )
    if len(non_limited) != args.expected_non_limited:
        raise AssertionError(
            "unexpected non-limited count: "
            f"expected={args.expected_non_limited} actual={len(non_limited)}"
        )
    return len(limited), len(non_limited)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        limited, non_limited = run(args)
    except (AssertionError, OSError, ValueError) as exception:
        print(f"FAIL: {exception}", file=sys.stderr)
        return 1
    print(
        "RATE_LIMIT_LOAD_PASS "
        f"requests={args.requests} limited={limited} non_limited={non_limited}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
