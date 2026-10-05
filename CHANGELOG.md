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

## Milestone M4
- **Dataset Pipeline (`ml/`)**:
  - Synthetic dataset generator (`ml/generate_dataset.py`) with fixed seed (42), independent held-out test templates (`heldout_test_templates.json`), and training templates (`train_templates.json`).
  - Total sizes: 20,000 train rows (60% benign, heavy on hard negatives: bank alerts, OTPs, deliveries, utilities, chats, scam awareness), 2,500 dev rows (60% benign), 3,200 frozen test rows (62.5% benign).
  - All splits grouped strictly by `group_id` with zero group leakage. Exact normalized texts deduplicated across splits.
  - Seed corpus (1,000 rows in `eval/corpus.jsonl`) kept completely out of training/dev/test as an extra check set.
- **Kotlin Featurizer v1 (`Featurizer.kt`, `MurmurHash3.kt`)**:
  - Entity placeholder replacement (`__url__`, `__phone__`, `__upi__`, `__amount__`, `__code__`, `__file_apk__`, `__brand_<kind>__`).
  - Word unigrams (`w|`), word bigrams (`b|`), character 3-5 grams (`c|`), and meta features (`m|`). Confirmed zero rule signal IDs in model features (§9.2).
  - MurmurHash3 32-bit x86 hash into $2^{18}$ buckets (262,144 buckets). Features scaled by $1 / \sqrt{k}$ ($L_2$ norm).
  - Token-to-bucket attribution mapping for explanation highlights.
- **Model Training & Calibration (`ml/train.py`)**:
  - Elastic-net Logistic Regression trained with SAGA solver and balanced weights on pinned requirements (`scikit-learn==1.5.2`, `numpy==2.0.2`, `scipy==1.13.1`).
  - Hyperparameters tuned on dev split: best $C=0.10, l1\_ratio=0.50$.
  - Target-smoothed Platt scaling fit on dev logits using Platt (1999) smoothed targets ($t_+ = (N_+ + 1)/(N_+ + 2), t_- = 1/(N_- + 2)$) with L-BFGS-B: $A=2.4863, B=-1.1068$. Guarantees numerical stability and ensures neutral/uninformative messages predict $p < 0.50$ ($m' = 0$).
  - int8 quantized weights packed into `packs/model/model.bin` with CRC32 (262,176 bytes).
  - Metadata written to `packs/model/model.json` including training-set SHA-256 hash, generator seed (42), per-language dev metrics, and operating thresholds.
- **Dynamic Header Sizing & Fallback (`LinearClassifier.kt`)**:
  - Model file size derived dynamically from header (`28 + (1 shl log2Buckets) + 4 = 262,176` bytes).
  - Validates CRC32 and featurizer version; falls back cleanly to rules-only on missing file or corruption.
- **Rules & Fusion Refinements (§10 & worked examples)**:
  - Pin worked examples to weights in `packs/rules.json`: P01 (0.20), L01 (0.55), S01 (0.10).
  - Verified that "L01 from number-only sender → DANGER" triggers via Combo C02 floor (0.85) rather than noisy-OR alone (0.595).
  - **OTP Negation & Evasion Handling**:
    - Contextual negation filtering ("do not share", "never share", "साझा न करें", "share na kare") prevents A01 from firing on genuine security notices; B01 fires with factor 0.40; verdict is NONE.
    - Evasion detection: scoped negation to the clause. When a "do not share" phrase is followed or preceded by a directive to send/forward the OTP to the sender (e.g. "don't share with anyone, send the OTP to me", "kisi ko mat batana, OTP mujhe bhejo"), negation is bypassed and A01 fires. Unit tested across `en`, `hi`, `hi-Latn`.
  - **Awareness-Context Suppression**:
    - To prevent false alarms on legitimate cyber safety advisories quoting scam threats, an awareness check suppresses soft threat signals (`P02` deadline, `P03` disconnection, `P04` legal threat).
    - Scope invariant: NEVER suppresses any hard signals (`L01`, `L10`, `L11`, `A01`, `A02`, `A04`) or any link signals (`L*`). Adversarially tested: scams wrapped in "beware of fraud" warnings still trigger DANGER.
  - Invariant 6: model alone capped below Danger ($0.719 < 0.72$).
  - At most 5 token attribution highlights emitted only when $m' > 0.2$.
- **Milestone M4 Verification & Per-Language Gates**:
  - **Tier 1 Gates on frozen test split (3,200 rows; 1,200 scam, 2,000 benign)**:
    - Overall Danger precision: 1.0 (Target: $\ge 0.97$) [PASS]
    - Overall Caution+ recall: 0.993 (Target: $\ge 0.90$) [PASS]
    - Overall Benign -> Danger: 0.0% (Target: $\le 0.3\%$) [PASS]
    - Overall Benign -> Caution+: 0.0% (Target: $\le 2.0\%$) [PASS]
  - **Per-Language Gates (§16.2)**:
    - `en`: 1,661 rows (709 scam, 952 benign) | Precision 1.0, Recall 1.0, B->Danger 0.0%, B->Caution 0.0% [PASS]
    - `hi`: 786 rows (217 scam, 569 benign) | Precision 1.0, Recall 0.963, B->Danger 0.0%, B->Caution 0.0% [PASS]
    - `hi-Latn`: 753 rows (274 scam, 479 benign) | Precision 1.0, Recall 1.0, B->Danger 0.0%, B->Caution 0.0% [PASS]
  - **Adversarial Evaluation**:
    - 488 rows (107 scam, 381 benign including shorteners, code-switching, spaced text).
    - Scam recall: 1.0, Benign False Positives: 0 (0.0%), Adversarial Precision: 1.0 [PASS]
  - **Rules-only vs Rules+ML**: Recall increased from 0.838 to 0.993 at 1.0 Danger precision.
  - **Seed corpus check set (1,000 rows)**: Danger precision 1.0, Recall 1.0, 0 false positives.
  - **Parity round-trip test**: Kotlin prediction matches Python reference within 0.01 tolerance (0.9754).
  - **Timing benchmark (`InferenceBenchmarkTest`)**: JVM 1,000-char analysis p95 = 2.08 ms (budget 150 ms); Featurize + Predict p95 = 0.47 ms (budget 15 ms).
  - **Full CI suite**: `./gradlew check assembleRelease` BUILD SUCCESSFUL.


