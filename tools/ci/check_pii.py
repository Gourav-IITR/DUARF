#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Gourav Mahunta

"""
tools/ci/check_pii.py
Enforces that no unredacted PII (Indian mobile numbers, bank account numbers,
raw OTP codes, UPI IDs, or private email addresses) is committed to the
repository (§2, §14, §19).
"""

import sys
import os
import re
import subprocess
import argparse

# Allowlisted phone numbers (documented test placeholders and non-Indian test fixtures)
ALLOWED_PHONES = {
    "+919876543210", "+91 98765 43210", "9876543210", "98765 43210", "+91-98765-43210",
    "+919123456789", "+91 91234 56789", "9123456789", "91234 56789",
    "+919000000000", "+91 90000 00000", "9000000000", "90000 00000",
    "+919999999999", "+91 99999 99999", "9999999999", "99999 99999",
    "+919876543211", "+91 98765 43211", "9876543211", "98765 43211",
    "+447911123456", "+44 7911 123456", "+44 7911123456", "7911123456",
    "18002583838"  # HDFC toll-free care helpline in test templates
}

# Allowlisted synthetic OTP codes used in tests, corpus, or documentation
ALLOWED_OTPS = {
    "123456", "654321", "000000", "111111",
    "492019", "382910", "592014", "392018"
}

# Allowlisted UPI IDs (documented test fixtures)
ALLOWED_UPI = {
    "user@okaxis",
    "helpme2024@ybl"
}

# Allowlisted email addresses (documented test fixtures)
ALLOWED_EMAILS = {
    "user@example.com",
    "support@example.com",
    "test@example.com"
}

# Regex patterns
# 1. Indian 10-digit mobile number starting with 6-9, optionally prefixed by +91
RE_PHONE = re.compile(r'(?<![\d.a-zA-Z])(?:\+91[\s-]?)?[6-9]\d{4}[\s-]?\d{5}(?![\d.a-zA-Z])')

# 2. Bank account number: 9-18 digits preceded by English or Hindi account keywords
RE_ACCOUNT = re.compile(
    r'(?i)(?:\b(?:a/c|account|ac\s*no|acc\s*no)\b|खाता(?:\s*(?:संख्या|नंबर|नं))?)\s*[:#.-]?\s*\d{9,18}\b'
)

# 3. OTP code: 4-8 digits next to OTP / ओटीपी keyword
RE_OTP = re.compile(
    r'(?i)(?:\botp\b|ओटीपी)\s*[:=is-]{0,15}\s*\b\d{4,8}\b|\b\d{4,8}\b\s*[:=is-]{0,15}\s*(?:\botp\b|ओटीपी)'
)

# 4. UPI ID: local@handle
RE_UPI = re.compile(r'\b[a-zA-Z0-9.\-_]{2,50}@([a-zA-Z0-9]+)\b')

# 5. Email address: local@domain
RE_EMAIL = re.compile(r'\b[a-zA-Z0-9._%+-]+@([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})\b')

RE_TOLL_FREE = re.compile(r'^(?:\+91[\s-]?)?1800\d{6,7}$')
RE_DIGITS = re.compile(r'\b\d{4,8}\b')

IGNORED_EXTENSIONS = {
    '.bin', '.apk', '.aab', '.jar', '.png', '.jpg', '.jpeg', '.ico', '.webp',
    '.class', '.dex', '.pyc', '.so'
}


def load_upi_handles() -> set:
    """Load known UPI handles from packs/lists/upi_handles.txt or fallback default."""
    repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    handles_path = os.path.join(repo_root, "packs", "lists", "upi_handles.txt")
    if os.path.isfile(handles_path):
        with open(handles_path, "r", encoding="utf-8") as f:
            return set(line.strip().lower() for line in f if line.strip() and not line.startswith("#"))
    return {
        "okaxis", "okhdfcbank", "oksbi", "okicici", "ybl", "ibl", "axl", "paytm",
        "apl", "upi", "postbank", "kotak", "barodampay", "aubank", "indus",
        "federal", "jupiteraxis", "fbl"
    }


UPI_HANDLES = load_upi_handles()


def is_allowed_email(addr: str, domain: str) -> bool:
    addr_lower = addr.lower()
    domain_lower = domain.lower()
    if addr_lower in ALLOWED_EMAILS:
        return True
    # RFC 2606 / RFC 6761 reserved example and testing domains
    if domain_lower == "example.com" or domain_lower.endswith(".example.com"):
        return True
    if domain_lower == "example.org" or domain_lower.endswith(".example.org"):
        return True
    if domain_lower == "example.net" or domain_lower.endswith(".example.net"):
        return True
    if domain_lower == "example" or domain_lower.endswith(".example"):
        return True
    if domain_lower == "test" or domain_lower.endswith(".test"):
        return True
    if domain_lower == "invalid" or domain_lower.endswith(".invalid"):
        return True
    if domain_lower == "localhost" or domain_lower.endswith(".localhost"):
        return True
    if domain_lower == "users.noreply.github.com" or domain_lower.endswith(".noreply.github.com") or domain_lower == "noreply.github.com":
        return True
    return False


def is_binary_or_ignored(filepath: str) -> bool:
    _, ext = os.path.splitext(filepath)
    return ext.lower() in IGNORED_EXTENSIONS


def check_content(content: str, source_name: str) -> list:
    violations = []
    lines = content.splitlines()
    for lno, line in enumerate(lines, 1):
        # Skip git author/committer headers in git log
        if line.startswith("Author: ") or line.startswith("Commit: "):
            continue

        # 1. Phone numbers
        for match in RE_PHONE.finditer(line):
            raw = match.group(0).strip()
            norm = raw.replace(" ", "").replace("-", "")
            if raw not in ALLOWED_PHONES and norm not in ALLOWED_PHONES:
                if not RE_TOLL_FREE.match(raw):
                    violations.append((source_name, lno, "PHONE", raw, line.strip()))

        # 2. Account numbers
        for match in RE_ACCOUNT.finditer(line):
            violations.append((source_name, lno, "ACCOUNT", match.group(0).strip(), line.strip()))

        # 3. OTP codes
        for match in RE_OTP.finditer(line):
            raw = match.group(0).strip()
            digits_match = RE_DIGITS.search(raw)
            if digits_match:
                code = digits_match.group(0)
                if code not in ALLOWED_OTPS:
                    violations.append((source_name, lno, "OTP", raw, line.strip()))

        # 4. UPI IDs (local@handle where handle in upi_handles.txt)
        for match in RE_UPI.finditer(line):
            handle = match.group(1).lower()
            if handle in UPI_HANDLES:
                raw = match.group(0).lower()
                if raw not in ALLOWED_UPI:
                    violations.append((source_name, lno, "UPI", match.group(0), line.strip()))

        # 5. Email addresses
        for match in RE_EMAIL.finditer(line):
            raw = match.group(0)
            domain = match.group(1)
            if not is_allowed_email(raw, domain):
                violations.append((source_name, lno, "EMAIL", raw, line.strip()))

    return violations


def get_staged_files() -> list:
    cmd = ["git", "diff", "--cached", "--name-only", "--diff-filter=ACM"]
    output = subprocess.check_output(cmd).decode("utf-8")
    return [f.strip() for f in output.splitlines() if f.strip()]


def get_tracked_files() -> list:
    cmd = ["git", "ls-files"]
    output = subprocess.check_output(cmd).decode("utf-8")
    return [f.strip() for f in output.splitlines() if f.strip()]


def scan_files(file_list: list) -> list:
    all_violations = []
    for filepath in file_list:
        if is_binary_or_ignored(filepath):
            continue
        if not os.path.isfile(filepath):
            continue
        try:
            with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
            violations = check_content(content, filepath)
            all_violations.extend(violations)
        except Exception as e:
            print(f"Warning: could not read {filepath}: {e}", file=sys.stderr)
    return all_violations


def scan_history() -> list:
    cmd = ["git", "log", "-p", "--all"]
    output = subprocess.check_output(cmd).decode("utf-8", errors="ignore")
    current_commit = ""
    current_file = ""
    all_violations = []

    for line in output.splitlines():
        if line.startswith("commit "):
            current_commit = line.split()[1]
        elif line.startswith("+++ b/"):
            current_file = line[6:]
        elif line.startswith("+") and not line.startswith("+++"):
            added_content = line[1:]
            loc = f"{current_commit[:8]}:{current_file}"
            violations = check_content(added_content, loc)
            all_violations.extend(violations)

    return all_violations


def main():
    parser = argparse.ArgumentParser(description="Scan repository for unredacted PII.")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--staged", action="store_true", help="Check only git staged files (pre-commit)")
    group.add_argument("--all", action="store_true", help="Check all tracked repository files (CI check)")
    group.add_argument("--history", action="store_true", help="Scan full git commit history")

    args = parser.parse_args()

    if args.history:
        print("Scanning full git commit history for PII...")
        violations = scan_history()
    elif args.staged:
        files = get_staged_files()
        print(f"Scanning {len(files)} staged file(s) for PII...")
        violations = scan_files(files)
    else:
        files = get_tracked_files()
        print(f"Scanning {len(files)} tracked file(s) for PII...")
        violations = scan_files(files)

    if violations:
        print(f"\n[ERROR] PII leakage detected! Found {len(violations)} violation(s):", file=sys.stderr)
        for loc, lno, kind, matched, sample in violations:
            print(f"  [{kind}] {loc}:{lno} -> '{matched}'", file=sys.stderr)
            print(f"         Snippet: {sample[:100]}", file=sys.stderr)
        print("\nPlease redact any personal phone numbers, bank accounts, real OTPs, UPI IDs, or personal emails before committing (§2, §14, §19).", file=sys.stderr)
        sys.exit(1)
    else:
        print(f"PII check passed: 0 violations found.")
        sys.exit(0)


if __name__ == "__main__":
    main()
