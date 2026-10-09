#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Gourav Mahunta

"""
tools/eval/intake_real_world.py
Intake tool for adding sanitized real-world messages (§19.2) into eval/real_world.jsonl.
Strictly validates schema, ensures redaction of all PII, and runs check_pii.py.
"""

import argparse
import json
import os
import re
import sys
import subprocess

VALID_CATEGORIES = {
    "PHISHING_BANK_KYC", "MALICIOUS_APK", "AUTHORITY_DIGITAL_ARREST",
    "UTILITY_DISCONNECT", "UPI_PAYMENT_FRAUD", "OTP_ACCOUNT_TAKEOVER",
    "REMOTE_ACCESS", "JOB_TASK", "INVESTMENT_TRADING", "LOAN_CREDIT",
    "DELIVERY_COURIER", "IMPERSONATED_CONTACT", "LOTTERY_PRIZE",
    "OTHER_SUSPICIOUS", "NONE"
}

VALID_LABELS = {"scam", "benign", "context_only"}
VALID_SENDER_KINDS = {"NUMBER_ONLY", "PERSONAL_NUMBER", "SAVED_CONTACT", "NAMED", "DLT_HEADER", "SHORT_CODE", "UNKNOWN"}

# Strict redaction check: names, phone numbers, accounts, amounts, OTPs
UNREDACTED_PHONE_REGEX = re.compile(r'(?<![\d.a-zA-Z])(?:\+91[\s-]?)?[6-9]\d{4}[\s-]?\d{5}(?![\d.a-zA-Z])')
UNREDACTED_ACCT_REGEX = re.compile(r'(?i)(?:\b(?:a/c|account|ac\s*no|acc\s*no)\b|खाता(?:\s*(?:संख्या|नंबर|नं))?)\s*[:#.-]?\s*\d{9,18}\b')
UNREDACTED_OTP_REGEX = re.compile(r'(?i)(?:\botp\b|ओटीपी)\s*[:=is-]{0,15}\s*\b\d{4,8}\b|\b\d{4,8}\b\s*[:=is-]{0,15}\s*(?:\botp\b|ओटीपी)')

ALLOWED_PHONES = {
    "+919876543210", "+91 98765 43210", "9876543210", "98765 43210", "+91-98765-43210",
    "+919123456789", "+91 91234 56789", "9123456789", "91234 56789",
    "+919000000000", "+91 90000 00000", "9000000000", "90000 00000",
    "+919999999999", "+91 99999 99999", "9999999999", "99999 99999",
    "+919876543211", "+91 98765 43211", "9876543211", "98765 43211",
    "18002583838"
}

ALLOWED_OTPS = {"123456", "654321", "000000", "111111", "492019", "382910", "592014", "392018"}

def validate_row(row_dict):
    errors = []
    required = ["id", "text", "label", "category", "lang", "sender_kind"]
    for field in required:
        if field not in row_dict or not str(row_dict[field]).strip():
            errors.append(f"Missing required field: '{field}'")

    if row_dict.get("label") not in VALID_LABELS:
        errors.append(f"Invalid label: '{row_dict.get('label')}'. Must be one of {VALID_LABELS}")

    if row_dict.get("category") not in VALID_CATEGORIES:
        errors.append(f"Invalid category: '{row_dict.get('category')}'. Must be one of {VALID_CATEGORIES}")

    if row_dict.get("sender_kind") not in VALID_SENDER_KINDS:
        errors.append(f"Invalid sender_kind: '{row_dict.get('sender_kind')}'. Must be one of {VALID_SENDER_KINDS}")

    text = row_dict.get("text", "")

    # Check unredacted phone numbers
    for m in UNREDACTED_PHONE_REGEX.finditer(text):
        val = m.group(0).strip()
        if val not in ALLOWED_PHONES:
            errors.append(f"PII VIOLATION: Unredacted phone number '{val}' detected in text. Redact to placeholder or standard test phone.")

    # Check unredacted account numbers
    for m in UNREDACTED_ACCT_REGEX.finditer(text):
        val = m.group(0).strip()
        errors.append(f"PII VIOLATION: Unredacted bank account '{val}' detected in text. Mask to e.g. 'XX1234'.")

    # Check unredacted OTPs
    for m in UNREDACTED_OTP_REGEX.finditer(text):
        val = m.group(0).strip()
        digits = re.search(r'\b\d{4,8}\b', val)
        if digits and digits.group(0) not in ALLOWED_OTPS:
            errors.append(f"PII VIOLATION: Unredacted OTP '{val}' detected. Redact to standard test OTP (e.g. 123456).")

    return errors

def main():
    parser = argparse.ArgumentParser(description="Intake tool for adding sanitized real-world rows to eval/real_world.jsonl")
    parser.add_argument("--row", help="JSON string representing the row to intake")
    parser.add_argument("--file", help="Path to JSON file containing row(s)")
    parser.add_argument("--dry-run", action="store_true", help="Validate without writing")
    args = parser.parse_args()

    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
    target_file = os.path.join(repo_root, "eval/real_world.jsonl")

    if not args.row and not args.file and not args.dry_run:
        parser.print_help()
        sys.exit(1)

    if args.dry_run and not args.row and not args.file:
        print("Dry run: Validating existing eval/real_world.jsonl...")
        with open(target_file, "r", encoding="utf-8") as f:
            for idx, line in enumerate(f, 1):
                if line.strip():
                    r = json.loads(line)
                    errs = validate_row(r)
                    if errs:
                        print(f"Row {idx} ({r.get('id')}): {errs}")
                        sys.exit(1)
        print("eval/real_world.jsonl is 100% compliant and PII-free.")
        return

    rows_to_process = []
    if args.row:
        rows_to_process.append(json.loads(args.row))
    elif args.file:
        with open(args.file, "r", encoding="utf-8") as f:
            data = json.load(f)
            if isinstance(data, list):
                rows_to_process.extend(data)
            else:
                rows_to_process.append(data)

    all_errors = []
    for r in rows_to_process:
        errs = validate_row(r)
        if errs:
            all_errors.append((r.get("id", "unknown"), errs))

    if all_errors:
        print("ERROR: Intake rejected due to validation / PII errors:")
        for rid, errs in all_errors:
            print(f"[{rid}]:")
            for e in errs:
                print(f"  - {e}")
        sys.exit(1)

    if args.dry_run:
        print(f"Dry run successful! All {len(rows_to_process)} row(s) valid and PII-free.")
        return

    with open(target_file, "a", encoding="utf-8") as f:
        for r in rows_to_process:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"Successfully appended {len(rows_to_process)} row(s) to {target_file}")

    # Run check_pii.py as second layer of defense
    pii_script = os.path.join(repo_root, "tools/ci/check_pii.py")
    subprocess.run([sys.executable, pii_script], check=True)

if __name__ == "__main__":
    main()
