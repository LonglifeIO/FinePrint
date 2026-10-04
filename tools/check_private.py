#!/usr/bin/env python3
"""pre-commit hook: fail if a file contains a local path, a Tailscale address or host, or any
private string listed in .git/private-patterns (one literal per line; that file is never committed,
so the strings it guards stay out of the repo too).

Usage (via pre-commit): check_private.py FILE...
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

PATTERNS = {
    "local home path": re.compile(r"/Users/"),
    # Tailscale hands out addresses in 100.64.0.0/10; other numbers like "100.0" are fine.
    "Tailscale address": re.compile(r"\b100\.(?:6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d{1,3}\.\d{1,3}\b"),
    # mDNS / tailnet host names ("macmini.local", "x.tailnet", "x.ts.net"), not "settings.local.json".
    "local host name": re.compile(r"[A-Za-z0-9-]\.(?:local|tailnet)(?![A-Za-z0-9_.-])|\.ts\.net\b"),
}


def private_patterns() -> dict[str, re.Pattern]:
    try:
        git_dir = subprocess.run(["git", "rev-parse", "--git-dir"], capture_output=True, text=True, check=True).stdout.strip()
        lines = Path(git_dir, "private-patterns").read_text(encoding="utf-8").splitlines()
    except (OSError, subprocess.CalledProcessError):
        return {}
    return {"private string": re.compile("|".join(re.escape(s.strip()) for s in lines if s.strip()), re.IGNORECASE)} \
        if any(s.strip() for s in lines) else {}


def main(paths: list[str]) -> int:
    patterns = {**PATTERNS, **private_patterns()}
    failed = False
    for path in paths:
        if Path(path).name == "LICENSE":
            continue
        try:
            text = Path(path).read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue  # binary or unreadable: gitleaks covers secrets in those
        for number, line in enumerate(text.splitlines(), 1):
            for label, pattern in patterns.items():
                if pattern.search(line):
                    # Never echo the matched private string itself.
                    print(f"{path}:{number}: {label}")
                    failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
