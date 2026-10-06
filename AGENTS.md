# Agent Instructions

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the authoritative specification of this project (DUARF).

## Core Rules
1. Section 2 invariants override everything else. If something conflicts with them, stop and ask.
2. Build in milestone order (Section 17). At the end of each milestone, run the full test and CI check suite, report results against that milestone's "Done when" criteria.
3. Stub items marked "verify" and section 19 inputs in `OPEN_QUESTIONS.md`. Never fabricate real-looking data.
4. Record all design decisions in `CHANGELOG.md`.
5. Redaction of recordings: When converting real notification recordings or message dumps into test fixtures, redact names, phone numbers, account numbers, amounts, and codes first. Only the redacted fixture is committed. Real recordings live only on the local machine and must remain gitignored.
