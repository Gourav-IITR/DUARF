# Changelog

All decisions made where the spec was silent or flexible are recorded here.

## Milestone M0
- Renamed project from working name "Pehredar" to "DUARF" per user instruction.
- Target package namespace: `com.duarf`.
- Java 21 toolchain used (matches Android Studio embedded JBR).
- Compile SDK 36, Target SDK 36, Min SDK 26.
- Custom CI tasks enforcing Section 2 invariants: zero network permissions, forbidden dependencies, no content logging, pure engine module.

## Milestone M1
- Pure Kotlin `:engine` module with zero Android dependencies.
- Text normalizer: NFKC, zero-width joiner preservation for Indic scripts, Cyrillic/Greek homoglyph folding, Indic digits mapping.
- Entity extractors: obfuscated URLs (hxxp, [.]), userinfo trick, IP literals, punycode, APK file extensions, Indian phones (+91, 10-digit), UPI handles, amounts, OTPs.
- Damerau-Levenshtein distance and Public Suffix List for lookalike brand domains.
- Signal engine covering S01-S03, L01-L12, A01-A09, P01-P14, T01, B01-B04.
- Combos C01-C10 with floor scores and category mapping.
- Score fusion with Noisy-OR formula and dampeners.
- Reason generation with priority weighting, deduplication by signal family, and highlight offsets.
- Engine CLI with `explain` and `eval` commands.
- Seed evaluation corpus (350 scam, 650 benign): Danger precision 1.0, recall 0.785.

## Milestone M2
- `:capture` Android library module.
- In-memory LRU deduplicator (capacity 500) with SHA-256 fingerprinting and 5-min/10-min short context window.
- `NotificationParser` extracting `MessagingStyle` unread messages, filtering out self messages, and deriving sender kind/country code.
- `WaNotificationListener` with bounded channel (capacity 64, drop oldest) to protect the main thread.
- `CheckMessageActivity` for system-wide text selection ("Check with DUARF") and share sheet intents.
- Debug-only `FakeWhatsAppPoster` for testing 1-to-1, group, number-only, and document notifications.

## Milestone M3
- `:data` Android library module with Room database: `AlertEntity`, `ConversationStatsEntity`, `SuppressedFingerprintEntity`, `DailyCounterEntity`.
- On-device encryption (§12): Android Keystore AES-256-GCM (random 12-byte IV prepended) + HMAC-SHA256 integrity check.
- Automated retention purge: alerts older than configured `retentionDays` purged on every alert insert and on demand.
- Privacy-compliant "Delete all data": purges all Room tables, wipes Keystore keys, and resets preferences.
- Pure numeric audited logging via `SafeLog`.
- `:app` UI in Jetpack Compose:
  - 6-step `OnboardingScreen` explaining offline protections and structural limitations (§18).
  - `HomeScreen` displaying protection status, weekly check stats, paste check box, and recent alerts.
  - `AlertDetailScreen` showing alert banner, formatted message with inline highlight spans, up to 3 prioritized reasons with evidence badges, 3-step actionable advice, feedback buttons, and sender trust toggle.
  - `CheckResultScreen` showing SAFE, CAUTION, or DANGER for pasted/shared messages.
  - `HistoryScreen` filterable by Danger/Caution alerts.
  - `SettingsScreen` with sensitivity, retention period, group alerts, language selector, and wipe data action.
  - `PrivacyProofScreen` performing live `PackageManager` permission inspection to verify zero internet permissions.
  - `AboutScreen` displaying engine and pack versions, cyber helpline 1930 guidance.
  - English and Hindi UI string localization (`values/strings.xml` and `values-hi/strings.xml`).
- Section 16.4 Privacy Tests:
  - Canary test: 200 benign messages containing unique canary tokens verified to never be stored or persisted in any database table or log.
  - Retention test: verified stale alerts are purged according to retention thresholds.
  - Wipe test: verified "Delete all data" completely clears all tables, resets preferences, and wipes crypto keys.
