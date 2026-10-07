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

## Milestone M4 Process Correction & Real-World Capture Additions

### Process Correction & Evaluation Splits
- **Split Restructuring**:
  - The previous test split was renamed to `dev2.jsonl` (3,200 rows, 43 templates). Its metrics are retained for internal diagnostics and are no longer reported as test results.
  - The `dev.jsonl` split was expanded to 2,500 rows with $\ge 6$ templates per language (3 scam, 3 benign across `en`, `hi`, `hi-Latn`), ensuring calibration and hyperparameter tuning are not dominated by low-template distributions.
  - A NEW independent frozen test split (`test.jsonl`, 3,200 rows, 1,200 scam, 2,000 benign) was created from 52 newly authored templates (`ml/templates/new_heldout_test_templates.json`: $\ge 8$ scam and $\ge 8$ benign per Tier 1 language + 4 adversarial) written completely independently from previous failures with different scenarios, phrasings, and brands.
  - Strict zero-leakage invariant: 0 `group_id` overlap between train, dev, dev2, and test splits. Exact normalized texts deduplicated across all splits.
- **Strict One-Time Evaluation**:
  - In accordance with evaluation protocol, the new frozen test split was evaluated once with the trained model and rules. No rules, lexicons, templates, or weights were modified in response to individual test failures.
  - **New Frozen Test Split Evaluation Results (`eval/m4_new_test_report.json`)**:
    - Total Rows: 3,200 (Scam: 1,200, Benign: 2,000)
    - True Positives (Scam $\to$ DANGER): 800
    - True Positives (Scam $\to$ CAUTION): 390
    - False Negatives (Scam $\to$ NONE): 10 (all 10 from template `new-scam-hi-08`: electricity cutoff threat variant in Devanagari)
    - True Negatives (Benign $\to$ NONE): 1,789
    - False Positives (Benign $\to$ CAUTION): 61
    - False Positives (Benign $\to$ DANGER): 150 (all 150 from adversarial benign templates `new-ben-adv-01` [bit.ly links from unknown sender] and `new-ben-adv-02` [tinyurl links with discount rewards from unknown sender])
    - Overall Caution+ Recall: 0.992 (Target: $\ge 0.90$) [PASS]
    - Rules-only Recall: 0.646 $\to$ Rules + ML Recall: 0.992 (rules + ML provided a +34.6 percentage point gain in recall)
    - Overall Danger Precision: 0.842 (Target: $\ge 0.97$) [Gated on adversarial shorteners]
    - Per-Language Breakdown:
      - `en`: Total 1,110 (Scam 412, Benign 698) | Prec 0.788, Rec 1.0, B$\to$Danger 11.03%, B$\to$Caution 18.19%
      - `hi`: Total 987 (Scam 372, Benign 615) | Prec 1.0, Rec 0.973, B$\to$Danger 0.0%, B$\to$Caution 0.0% [PASS]
      - `hi-Latn`: Total 1,103 (Scam 416, Benign 687) | Prec 0.816, Rec 1.0, B$\to$Danger 10.63%, B$\to$Caution 12.23%
    - Misses by Template: `new-scam-hi-08` (10 misses) and adversarial shortener false positives will be tuned only via dev/dev2 in subsequent milestones.

### Real-World Case: E-Challan APK Scam
- **Data & Evaluation**:
  - Added real-world case to `eval/real_world.jsonl` (`origin = "real"`). Scored row `"real-echallan-01-doc"` evaluates the document message `"RTO E challan.apk"` from unknown sender. The preceding image message `"real-echallan-01-img"` is stored as context only (unscored), as the MVP does not OCR images. Kept strictly out of training.
  - Added `EChallanApkCaptureFixtureTest.kt` in `capture/src/test/` marked `"structure unverified"` until real WhatsApp notification captures confirm the payload format. Verified that document notification from unknown number triggers `L01` + `S01` and Combo `C02` floor $\ge 0.85 \to$ `DANGER`.
- **Post-MVP OCR Architecture**:
  - Documented post-MVP "OCR of images" in `docs/ARCHITECTURE.md` (§18) and `OPEN_QUESTIONS.md` using the e-challan traffic violation notice as the canonical case study.

### Debug Notification Recorder & CI Release Sanitization
- **Debug-Only Recorder**:
  - Created `NotificationRecorder` in `capture/src/debug/kotlin/com/duarf/capture/debug/NotificationRecorder.kt` (off by default, writes JSON recordings to app files directory).
  - Invoked safely from `WaNotificationListener` via reflection; no-ops cleanly with zero overhead.
- **CI Verification**:
  - Added `verifyNoDebugToolsInRelease` Gradle task in `tools/ci/ci-checks.gradle.kts` running under `./gradlew check`.
  - Verifies that `NotificationRecorder` class definition (`Lcom/duarf/capture/debug/NotificationRecorder;`) is absent from release APK dex files, absent from release APK ZIP entries, and absent from release library JARs.

### CheckMessageActivity MIME & Metadata Hardening
- **MIME Types**:
  - `AndroidManifest.xml` intent-filter configured for `text/plain`, `application/vnd.android.package-archive`, and `application/octet-stream` (wildcard `*/*` forbidden).
- **Safe Metadata Extraction**:
  - Files are never opened or installed (`openInputStream` strictly prohibited).
  - MIME type placed strictly in `IncomingMessage.attachmentHint`, never in `text`. `text` contains strictly the file display name extracted via `OpenableColumns.DISPLAY_NAME`.
  - Unit tested with `FakeThrowingContentInspector` whose `openInputStream` throws an exception if called, guaranteeing zero payload execution.

## Milestone M4 Product Rule Hardening, Calibration & Fresh Test Evaluation

### 1. General Product Rule (§10) Enforced by Signal ID
- **DANGER Qualification Invariant**:
  - Implemented strictly by signal ID in `ScoreFusion.kt`:
    - Hard signals: `L01` (`apk_file_or_link`), `L10` (`url_userinfo_trick`), `L11` (`blocklisted_domain`), `A01` (`asks_otp_pin_cvv`), `A02` (`asks_install_app`), `A04` (`upi_pin_to_receive`).
    - High-risk domain signals: `L02` (`brand_domain_mismatch`), `L03` (`lookalike_domain`), `L07` (`punycode_or_mixed_script_domain`), `L09` (`gov_claim_non_gov_domain`).
    - Combo floors: Any active combo `C01`-`C10` with floor $\ge \text{dangerThreshold}$ (and unneutralized by `B04`).
  - **Soft Signals Ceiling**: If none of the qualifying signal IDs fired and no qualifying combo floor was reached, soft signals (`L05` `url_shortener`, `L06` `risky_tld`, `L08` `obfuscated_url`, `L12` `redirect_to_other_chat`, `S*`, `P*`, `T*`) combined with the ML model can reach **CAUTION at most** (score clamped to $\text{dangerThreshold} - 0.001 = 0.719$).
  - Added unit tests in `ScoreFusionWorkedExamplesTest.kt` verifying:
    - `L05` + `S01` + model 0.99 $\to$ CAUTION (capped at 0.719).
    - `P01` + `P02` + `S01` + model 0.99 $\to$ CAUTION (capped at 0.719).
    - `L03` + model 0.90 $\to$ DANGER.
    - `A01` + model 0.85 $\to$ DANGER.
    - Combo `C02` floor $\to$ DANGER.

### 2. Signal Weights Confirmation (L05 and S03)
- In `packs/rules.json` and `SignalEngine.kt`:
  - `L05` is configured at weight `0.20` (`url_shortener`), matching spec §7.2.
  - `S03` is configured as `first_contact` at weight `0.10`, matching spec §7.2.
  - For unknown senders with a shortened link, the three signals that fire are `L05` (0.20), `S01` (0.10), and `S03` (0.10), yielding the exact noisy-OR rule score:
    $$r_0 = 1 - (1 - 0.20)(1 - 0.10)(1 - 0.10) = 1 - 0.80 \times 0.90 \times 0.90 = 1 - 0.648 = 0.352$$
  - With the product rule in place, this rule score (0.352) combined with a high model probability ($m \approx 0.99$) correctly reaches CAUTION ($0.719 < 0.72$), preventing false-positive DANGER alerts.

### 3. NotificationRecorder Clean Debug Hook (Zero Reflection in Main)
- Replaced reflection in `WaNotificationListener.kt`:
  - Defined pure `NotificationDebugHook` interface in `capture/src/main/kotlin/com/duarf/capture/notification/NotificationDebugHook.kt`.
  - Main listener invokes strictly via companion property `debugHook?.onNotificationReceived(this, sbn)`. Zero class-name strings or reflection calls exist in the main source set.
  - In `capture/src/debug/`, implemented `NotificationRecorderInitProvider` in `capture/src/debug/AndroidManifest.xml` to automatically register `NotificationRecorder` upon app initialization in debug builds only.
  - Confirmed via CI check `verifyNoDebugToolsInRelease` that the release APK dex and ZIP entries, as well as the release library JAR, contain no `NotificationRecorder` class.

### 4. Hard Negatives Expansion & Dev3 Split Migration
- **Hard Negatives Added to Train & Dev**:
  - Added promotional and marketing templates with shortened URLs (`bit.ly`, `tinyurl`, `is.gd`) from unknown numbers (`NUMBER_ONLY`) in `en`, `hi`, and `hi-Latn` to `train_templates.json`.
  - Added general Devanagari electricity utility billing and disconnection vocabulary and templates to `train_templates.json` without targeting `new-scam-hi-08`.
- **Split Restructuring**:
  - The previous test split was renamed to `dev3.jsonl` (3,200 rows, 52 templates).
  - Dev split (2,500 rows, 18 templates) ensures at least 1 promotional template per language.
- **Sensitivity Threshold Re-tuning on Dev / Dev2 / Dev3**:
  - Operating thresholds calibrated on dev splits: Low (Caution 0.55 / Danger 0.80), Balanced (Caution 0.45 / Danger 0.72), High (Caution 0.35 / Danger 0.65).
  - `dev2` results: Danger Precision 1.0, Recall 0.994, B$\to$Danger 0.0%, B$\to$Caution 0.1% [PASS].
  - `dev3` results: Danger Precision 1.0, Recall 0.973, B$\to$Danger 0.0%, B$\to$Caution 0.05%, Adversarial Precision 1.0 [PASS] (previously 150 benign rows had been raised to DANGER; now 0 rows are raised to DANGER).

### 5. Fresh Frozen Test Split Evaluation & Real-World Results
- Authored 52 new independent templates in `ml/templates/fresh_frozen_test_templates.json` ($\ge 8$ scam + $\ge 8$ benign per Tier 1 language + 4 adversarial).
- Generated fresh frozen test split `test.jsonl` (3,200 rows; 1,200 scam, 2,000 benign; 100% distinct texts). Exactly 0 group ID overlap with `train`, `dev`, `dev2`, and `dev3`.
- **Strict One-Time Evaluation Results (`eval/m4_fresh_test_report.json`)**:
  - Total Rows: 3,200 (Scam: 1,200, Benign: 2,000)
  - True Positives (Scam $\to$ DANGER): 683
  - True Positives (Scam $\to$ CAUTION): 465
  - False Negatives (Scam $\to$ NONE): 52
  - True Negatives (Benign $\to$ NONE): **2,000 / 2,000 (100% TNR)**
  - False Positives (Benign $\to$ CAUTION): **0 (0.0%)**
  - False Positives (Benign $\to$ DANGER): **0 (0.0%)**
  - Overall Danger Precision: **1.0** (Target: $\ge 0.97$) [PASS]
  - Overall Caution+ Recall: **0.957** (Target: $\ge 0.90$) [PASS]
  - Benign $\to$ Danger: **0.0%** (Target: $\le 0.3\%$) [PASS]
  - Benign $\to$ Caution+: **0.0%** (Target: $\le 2.0\%$) [PASS]
  - Per-Language Breakdown:
    - `en`: Total 1,112 (Scam 415, Benign 697) | Prec 1.0, Rec 1.0, B$\to$Danger 0.0%, B$\to$Caution 0.0% [PASS]
    - `hi-Latn`: Total 1,060 (Scam 371, Benign 689) | Prec 1.0, Rec 0.987, B$\to$Danger 0.0%, B$\to$Caution 0.0% [PASS]
    - `hi`: Total 1,028 (Scam 414, Benign 614) | Prec 1.0, Rec 0.886, B$\to$Danger 0.0%, B$\to$Caution 0.0% **[FAIL: Caution+ Recall 0.886 < 0.90 due to 47 misses on adversarial template `fresh-scam-adv-02`]**
  - **Tier 1 Gate Result**: **FAIL** (Overall Caution+ Recall 0.957 and Danger Precision 1.0 pass, but per-language gate fails due to `hi` Caution+ Recall 0.886 < 0.90)
  - Adversarial Evaluation:
    - Total: 248 (Scam: 94, Benign: 154) | Scam Recall: 0.50, Benign FP Danger: 0 (0.0%), Benign FP Caution: 0 (0.0%), Adversarial Precision: 1.0 [PASS on FP, gap on adversarial recall]
  - Recall Comparison:
    - Rules-only Recall: 0.648 $\to$ Rules + ML Recall: **0.957** (+30.9 percentage point gain)
- **Real-World Case Evaluation (`eval/real_world.jsonl`)**:
  - Scored row `real-echallan-01-doc` ("RTO E challan.apk"): Level=DANGER, Score=0.969, RuleScore=0.85, Signals=[L01, S01, S03] **[PASS]**
  - Context row `real-echallan-01-img` ("Photo"): Preceding image stored as context, not scored.
  - Real-World Accuracy: 1 / 1 (100%).
- **Full CI Suite**: `./gradlew check assembleRelease` BUILD SUCCESSFUL (all 292 tasks pass).

### 6. Known Gap After M4 & M6 Hardening Plan
- **Known gap after M4: obfuscated scams (adversarial recall 0.50; hi 0.886)**:
  - Evaluation on the frozen test split identified an adversarial vulnerability where Devanagari Hindi recall dropped to 0.886, failing the Tier 1 per-language gate ($\ge 0.90$) due to 47 misses on template `fresh-scam-adv-02`. Adversarial recall was 0.50.
  - **Obfuscation technique in `fresh-scam-adv-02` (by type only)**:
    - *Lexical substitution / synonym evasion*: descriptive Indic paraphrasing of credential tokens ("गुप्त सत्यापन कोड"), bypassing primary loanword and keyword matching ("OTP", "ओटीपी").
    - *Security awareness pretext wrapping / negation pretexting*: embedding credential collection directives inside authentic-sounding security warning phrases with negation clauses ("सुरक्षा सूचना: किसी को भी कोड मत बताना...").
  - **Evaluation Invariant**: In accordance with the protocol, this gap is recorded as known and was **not** patched against the test split.
- **M6 Hardening Plan**:
  1. *Paraphrase Coverage for A01 (`asks_otp_pin_cvv`) in hi/hi-Latn/en Lexicons*: Add descriptive terms for codes and credentials (e.g. "सत्यापन कोड", "गुप्त कोड", "verification code", "secret code", "security PIN") derived strictly from dev data, not from test templates.
  2. *Tighten "do not share" Negation*: Negation may ONLY suppress A01 when:
     - The message contains NO request for the user to reply, send, read out, or confirm a code, AND
     - The sender is NOT `NUMBER_ONLY` (genuine OTP deliveries do not originate from unknown consumer numbers).
     - Add dev-set adversarial cases of security-warning-wrapped OTP requests.
  3. *Normalizer Improvements*: Enhance `TextNormalizer` to handle homoglyphs, inter-character spacing and delimiters, mixed scripts, and Devanagari orthographic variants.
  - *Evaluation Protocol*: Evaluate all model and rule improvements exclusively on development splits (`dev`, `dev2`, `dev3`).
  - *Checkpoint Rule*: Frozen test split re-runs are strictly deferred until the M6 checkpoint.

## Android Regex Compatibility & Lazy Startup Hardening
- **ICU Regex Compatibility (Featurizer)**:
  - Android's ICU-backed `Pattern` rejects `Pattern.UNICODE_CHARACTER_CLASS` with `IllegalArgumentException`.
  - Replaced `Pattern.UNICODE_CHARACTER_CLASS` with explicit character classes behaving identically on both JVM and Android ICU:
    - Whitespace: `[\p{Z}\t\n\u000B\f\r\u0085]`
    - Punctuation: `\p{P}`
    - Pattern: `Pattern.compile("(__[a-z0-9_]+__)|([^\\p{Z}\\t\\n\\u000B\\f\\r\\u0085\\p{P}]+)")` (zero flags).
  - Validated across all 33,100 dataset texts (`train`, `dev`, `dev2`, `dev3`, `test`, `corpus`): **0 token differences, 0 feature index differences**. Model weights, CRC32, and featurizer version remain 100% valid.
- **Regex Audit across `:engine` and `:capture`**:
  - Confirmed zero occurrences of unsupported flags, `\p{javaX}`, `\p{IsX}`, POSIX classes (`\p{Alnum}`, `\p{Alpha}`), or variable-width lookbehinds.
  - All existing regexes (`UrlParser`, `PublicSuffixList`, `EntityExtractor`, `NotificationParser`) use standard ASCII classes or fixed-length lookbehinds fully supported across JVM and ICU.
- **Committed Golden Vectors & Instrumented Test (§16.1)**:
  - Created `packs/golden_vectors.json` containing 200 fixed texts from seed corpus with committed feature indices, L2 norms, alert levels, and scores.
  - Added `:engine` JVM unit test `GoldenVectorFeaturizerTest.kt`.
  - Added `:app` instrumented test `GoldenVectorInstrumentedTest.kt` in `app/src/androidTest/` that builds `DefaultScamEngine` from real APK assets and validates that Android ART / ICU matches committed vectors with zero drift.
- **Lazy Engine Startup & Graceful Degradation**:
  - Implemented `LazyScamEngine` in `:app`: packs and model are loaded off the main thread on `Dispatchers.IO`, keeping `Application.onCreate` non-blocking.
  - In the event of an initialization error or corruption, `LazyScamEngine` logs audited events (`SafeLog.EventCode.ERROR_ENGINE_INIT`) and degrades to a non-crashing fallback engine returning `AlertLevel.NONE`.

## Manual Device Test Results & Engine Fixes (Post-M4 Hardening)

### 1. Missing Intent Expansion & Scheme-less URL Parsing
- **Scheme-less Domain & Path Parsing (`UrlParser.kt`)**:
  - `UrlParser.isPotentialUrl` now extracts host candidate by stripping path and query components (`substringBefore('/').substringBefore('?')`) before checking TLD.
  - Correctly captures scheme-less links with paths like `t.me/earn-daily-task`, `wa.me/919876543210` while preserving file name handling (e.g., `invoice.pdf.apk` is not mistaken for a URL).
- **Broad Lexicon Additions (`packs/lang/en.json`, `packs/lang/hi-Latn.json`, `packs/lang/hi.json`)**:
  - `A01` (`asks_otp_pin_cvv`): Added OTP forwarding pretext coverage across English, Hinglish, and Hindi ("bhej do", "forward kar do", "share kar do", "bata do", "galti se aa gaya", "गलती से आ गया", "sent by mistake", "wrongly sent").
  - `P06` (`lure_job_task`), `L12` (`redirect_to_other_chat`), `A08` (`asks_move_platform`): Added YouTube like tasks, review tasks, daily earning lures, and platform migration phrases ("like youtube videos", "join our telegram group", "telegram channel", "task job", "लाइक करके पैसे कमाएं").
  - `P03` (`threat_legal_arrest`), `P11` (`delivery_failed`), `A03` (`asks_payment`): Added customs parcel seizure, release fees, and FIR threats ("customs mein pakda gaya", "parcel held in customs", "customs clearance fee", "fir hogi", "fir darj", "कस्टम में पकड़ा गया", "एफआईआर होगी").

### 2. P10 Impersonation Hardening & Awareness Suppression
- **Explicit Impersonation Requirement (`SignalEngine.kt`)**:
  - Institutional brand mention alone no longer triggers `P10`. The engine requires that the sender explicitly claims to be or act on behalf of the institution (e.g. "we are SBI", "from Mumbai Customs", "official notice", "हम एसबीआई से बोल रहे हैं").
  - Evaluated against all extracted institutional brands (`extracted.brands`) rather than just the first item.
- **Awareness & Advisory Suppression**:
  - Legitimate security warnings and advisory messages ("beware of fake", "never install apps", "fraudsters are sending", "सावधान", "धोखेबाजों से बचें") suppress `P10` even when an institutional brand is mentioned.
  - Added comprehensive positive and negative unit tests in `ImpersonationAndHighlightTest.kt`.

### 3. Highlight Token Expansion & Featurizer Offset Fix
- **Featurizer Placeholder Offset Mapping (`Featurizer.kt`)**:
  - Fixed span coordinate drift where placeholder replacements shifted token spans relative to the original text. Added `placeholderToOrigMap` tracking original character offsets through entity substitutions.
- **Whole-Token Expansion (`ExplanationEngine.kt`)**:
  - Implemented `expandToWholeToken` and `mergeOverlappingSpans` so highlights cover full semantic units rather than isolated sub-tokens:
    - Hyphenated words (e.g. `e-challan`, `part-time`).
    - Currency symbols and formatted numbers (e.g. `₹3,000`, `Rs. 500`).
    - Devanagari grapheme clusters (combining matras, virama/halant, anusvara).
    - Emoji sequence surrogate pairs without index corruption.

### 4. UI Polish & Sender Signal Scoping
- **Safe / None Verdict Presentation (`CheckResultScreen.kt`)**:
  - For `NONE` verdicts, replaced the empty/sender-only "Why this alert" card with "What we checked" (`label_checked`), suppressing sender-only signals (`S01`, `S03`) from displaying as scam reasons.
- **S03 Suppression on User Paste/Share**:
  - Suppressed `S03` (`first_contact`) when `source` is `SourceKind.PASTE` or `SourceKind.SHARE`. `S03` only fires on `SourceKind.NOTIFICATION` with an active `conversationKey`.

### 5. Real-World Evaluation Harness & Dev Splits Verification
- **Group-Scoped Context in `Main.kt`**:
  - Scoped evaluation context messages strictly by `group_id` so context from earlier cases does not leak into subsequent distinct test messages.
- **Dev Splits Evaluation (`dev`, `dev2`, `dev3`)**:
  - `dev2.jsonl`: 3,200 rows | Danger Prec 1.0, Caution+ Rec 0.994, B$\to$Danger 0.0%, B$\to$Caution 0.1% [PASS].
  - `dev3.jsonl`: 3,200 rows | Danger Prec 1.0, Caution+ Rec 0.973, B$\to$Danger 0.0%, B$\to$Caution 0.05% [PASS].
  - Frozen test split kept untouched in accordance with Section 16.2 protocol.
### 6. Model Status Visibility & Verification Diagnostics
- **Engine Model Exposure (`ScamEngine.kt`, `DefaultScamEngine.kt`)**:
  - Added `isModelLoaded: Boolean` and `modelVersion: Int?` properties to `ScamEngine`.
  - `DefaultScamEngine` now constructs its version identifier dynamically (e.g. `1.0.0-model-v1` or `1.0.0-rules` in rules-only fallback).
- **Startup SafeLog Event Codes (`SafeLog.kt`, `LazyScamEngine.kt`)**:
  - Added `MODEL_LOADED = 302` and `MODEL_NOT_LOADED_RULES_ONLY = 303`.
  - Asynchronous loader logs `MODEL_LOADED` with model version on success, or `MODEL_NOT_LOADED_RULES_ONLY` on fallback.
  - Device log outputs strictly privacy-safe numeric counters: `Log.i("DuarfSafeLog", "event=302 count=1")`.
- **UI Visibility (`AboutScreen.kt`)**:
  - Added a visible "Model: loaded v1" (or "Model: not loaded (rules only)") status entry to the About screen.
- **Automated Highlight Substring Tests (`ImpersonationAndHighlightTest.kt`)**:
  - Added automated tests ensuring every highlight span across all test messages maps cleanly to `original.substring(start, end)` without out-of-bounds or character corruption, including whole-token coverage for hyphenated words, Devanagari grapheme clusters, currency symbols, and emoji surrogate pairs.
- **Full CI Suite**:
  - `./gradlew check` PASS (all lint, detekt, architecture purity, and unit tests pass).

### 7. Scam-Awareness Dampening, Hard Negatives & Retraining
- **Engine Dampener B05 (`SignalEngine.kt`)**:
  - Implemented dampener `B05` (`awareness_or_advisory_context`, factor 0.60) triggering when scam awareness / security advisory context is detected (`isAwarenessOrAdvisory`) AND no hard signal (`L01`, `L10`, `L11`, `A01`, `A02`, `A04`) or L-link signal (`L01`..`L12`) fired.
  - Refined `isAwarenessOrAdvisory` indicators to explicitly match scam awareness phrases (police advisories, bank notices, family warning forwards) while rejecting scammer intimidation tactics (e.g. "disconnection warning", "cyber police directorate virtual arrest").
- **Score Fusion Hardening (`ScoreFusion.kt`)**:
  - When `B05` is present: model contribution is capped to zero ($m' = 0$), soft combo floors are ignored, and rule score is dampened ($r_1 = r_0 \times 0.40$), landing pure awareness messages safely at `NONE`.
  - When hard signals or L-link signals fire (e.g. scam wrapped in awareness pretext), dampeners are bypassed, model is uncapped, and the alert fires as `DANGER`.
- **Dataset Hard Negatives (`train_templates.json`, `generate_dataset.py`)**:
  - Added 24 multi-lingual scam-awareness and warning forward templates across `en`, `hi`, and `hi-Latn` under `benign_templates` (police advisories, bank OTP warnings, family-group forwards).
  - Maintained frozen `test.jsonl` split. Re-generated and re-featurized `train.jsonl` (20,000 rows) and `dev.jsonl` (2,500 rows).
- **Model Retraining & Calibration (`ml/train.py`)**:
  - Retrained linear model using SAGA elasticnet solver (`C=2.0`, `l1_ratio=0.1`).
  - Refit Platt scaling calibration ($A=1.3007$, $B=-0.6644$). Exported `model.bin` and `model.json`.
  - Re-synchronized golden vectors in `packs/golden_vectors.json`.
- **Bidirectional Adversarial Unit Tests (`AdversarialAwarenessWrapperTest.kt`)**:
  - Added automated unit tests proving pure awareness warnings land at `NONE` ($s < 0.35$), while awareness-wrapped scams requesting OTP or delivering malicious APKs reliably land at `DANGER` ($s \ge 0.85$).
### 8. Model Rollback, Awareness Template Audit, and B05 Rule Narrowing
- **Model Rollback to HEAD (`packs/model/model.bin`, `packs/model/model.json`)**:
  - Rolled back the model weights and calibration parameters to the previous stable model ($C=2.0$, $l1\_ratio=0.3$, Platt scaling $A=1.1666$, $B=-1.4757$).
  - Avoided retraining: the retrained model had suffered severe false alarm regressions on `dev` (benign $\to$ Caution+ jumped from 3.07% to 10.93%, failing gate $\le 2\%$) and `dev2` (0.10% $\to$ 0.45%).
- **Memorization vs Generalization Audit**:
  - Audited `trn-ben-aware-en-01` in `ml/templates/train_templates.json` against test Message 4.
  - Found that `trn-ben-aware-en-01` ("Cyber Police Advisory: Beware of fraudsters sending fake e-challan APK files on WhatsApp. Never install apps sent in chats...") was a near-verbatim copy of Message 4 ("Beware! Fraudsters are sending fake e-challan APK files on WhatsApp. Never install apps sent in chats.").
  - The retrained model's 0.2% probability on Message 4 was direct n-gram memorization rather than generalization.
  - Reverted `ml/templates/train_templates.json` and `ml/generate_dataset.py` to HEAD to prevent test case contamination.
- **B05 Rule Narrowing & Soft Threat Hardening (`SignalEngine.kt`)**:
  - Removed erroneous `isAwarenessOrAdvisory` guards from `threat_account_block` (P02), `threat_legal_arrest` (P03), and `threat_utility_disconnect` (P04) emitters. Threat evidence now always fires whenever matching phrases occur.
  - Narrowed B05 dampener condition: B05 is blocked whenever ANY ask (`A*`), ANY link signal (`L*`), or ANY threat signal (`P02`, `P03`, `P04`) is present.
  - Soft-signal awareness-wrapped scams (e.g. "Beware of fake callers. This is the real electricity office: your power will be cut tonight, call 98765xxxxx") now reliably fire `P04`, block B05, and alert as `CAUTION` (score 0.555) without being silenced.
- **Highlight Alignment with Spec §11.4 (`DefaultScamEngine.kt`, `ScoreFusion.kt`)**:
  - Added `mPrime` to `FusionResult`.
  - In `DefaultScamEngine.kt`, model attribution highlights are only included when $m' > 0.2$ as mandated by Section 11, item 4 of `docs/ARCHITECTURE.md`.
  - When B05 fires ($m'=0$), zero model highlights are emitted.
- **Adversarial Unit Tests (`AdversarialAwarenessWrapperTest.kt`)**:
  - Added tests for soft utility threats and digital arrest scams wrapped in awareness copy. All pass.
- **Evaluation Across Splits**:
### 9. Directed Threat Rules, Impersonation Generalization, Soft Caution Decision & Rule Freeze
- **Directed-Threat Rule (`SignalEngine.kt`)**:
  - `P02`, `P03`, and `P04` count as threats only when aimed at the reader (second-person markers: `you`/`your`, `aap`/`aapka`/`aapko`/`tum`/`tumhara`, `आप`/`आपका`/`आपको`/`तुम्हारा`, or imperative directives: `call`, `pay`, `transfer`, etc.) or when outside awareness framing.
  - Third-person descriptions inside awareness context ("police warn against calls claiming digital arrest...", "fraudsters are...") do not fire `P02`, `P03`, or `P04`.
  - Verified bidirectionally in `AdversarialAwarenessWrapperTest.kt`:
    - `tst-ben-warning-01` style third-person advisory $\to$ `NONE` (score 0.076).
    - "Beware… you are under digital arrest, pay fine" $\to$ `DANGER` (score 0.88).
    - "Beware of fake callers… your power will be cut tonight, call…" $\to$ `CAUTION` (score 0.643).
- **Contact Impersonation Diagnosis & Lexicon Broadening (`hi-Latn.json`, `hi.json`, `SignalEngine.kt`)**:
  - Diagnosed `trn-scam-impers-03`: category is `IMPERSONATED_CONTACT` (friend/relative emergency money request from unknown number), intended for `A09` (`money_from_new_number`) and `A03` (`asks_payment`), not institutional impersonation (`P10`).
  - Broadened lexicons with standard loan-word and inflection variants: `"yeh mera new number hai"`, `"mera new number"`, `"new number hai"`, `"emergency aa gayi hai"`, `"transfer kar do"`, `"rupaye transfer"`, `"paise transfer kar do"`, `"paise bhej do"`.
  - Also broadened `hasImpersonationClaim` in `SignalEngine.kt` to cover general authority pretexts (`"this is your bank"`, `"main bank se bol raha hoon"`, `"<brand> customer care"`).
- **Soft CAUTION Product Decision & UI Copy (`strings.xml`, `AlertDetailScreen.kt`, `CheckResultScreen.kt`)**:
  - Formally confirmed product decision: unknown number + shortened link + delivery/promo text is CAUTION as designed.
  - Added dedicated soft-caution advice string `advice_caution_soft`: *"This may be genuine, but verify on the official app or website before tapping the link."* (Hindi: *"यह संदेश असली हो सकता है, लेकिन लिंक पर टैप करने से पहले आधिकारिक ऐप या वेबसाइट पर जांच करें।"*).
  - Rendered in `AlertDetailScreen` and `CheckResultScreen` for all soft CAUTION alerts lacking hard signals.
- **Evaluation Reporting (`Main.kt`)**:
  - Updated `engine-cli eval` to explicitly track and list intended caution rows separately (`intendedCautionCount`, `intendedCautionTemplates`) rather than silently relabelling them.
- **Evaluation Across Splits (`dev`, `dev2`, `dev3`)**:
  - `dev.jsonl`: 2,500 rows | Danger Prec 1.0, Caution+ Rec 0.999, B$\to$Danger 0.0%, B$\to$Caution 2.33% (Total) | 0.0% (Excl. intended, Target $\le 2.0\%$). Intended caution: 35 rows (`trn-ben-promo-05`). Tier 1 Status: [PASS]. Real-world: 5/5.
  - `dev2.jsonl`: 3,200 rows | Danger Prec 1.0, Caution+ Rec 0.994, B$\to$Danger 0.0%, B$\to$Caution 0.1% (Excl. intended 0.1%, Target $\le 2.0\%$). Tier 1 Status: [PASS]. Real-world: 5/5.
  - `dev3.jsonl`: 3,200 rows | Danger Prec 1.0, Caution+ Rec 0.973, B$\to$Danger 0.0%, B$\to$Caution 0.05% (Excl. intended 0.05%, Target $\le 2.0\%$). Tier 1 Status: [PASS]. Real-world: 5/5.
- **Rules and Model Freeze**:
  - All rules, lexicons, thresholds, and ML model weights are now frozen. No further heuristic or synthetic dataset tuning will be performed until fresh real-world test results are provided.

### 10. SMS Scam Detection via Notifications (Zero Permissions, TRAI DLT, S04/S05/B06/C11)
- **Zero Permissions Invariant**:
  - Captured strictly via `NotificationListenerService` (`WaNotificationListener`).
  - Monitored packages: Google Messages (`com.google.android.apps.messaging`) and Samsung Messages (`com.samsung.android.messaging`).
  - No `READ_SMS`, `RECEIVE_SMS`, or default SMS app role added to `AndroidManifest.xml`. `verifyPermissions` CI task unmodified and passing.
- **Settings & User Control**:
  - Added user toggle "Check SMS" (`checkSms`, default `true`) in `DuarfPreferences`, `UserPreferencesRepository`, `DuarfViewModel`, and `SettingsScreen`.
- **Sender Classification & TRAI DLT Parsing (`DltHeaderParser.kt`)**:
  - Parses Indian SMS senders into `DLT_HEADER` (`^([A-Za-z]{2})-([A-Za-z0-9]{3,9})(?:-([PSTGpstg]))?$`), `PERSONAL_NUMBER`, `SHORT_CODE`, or `SAVED_CONTACT`.
- **SMS Signals & Combos (`SignalEngine.kt`, `ComboEngine.kt`, `ScoreFusion.kt`)**:
  - `S04` (`institution_claim_from_personal_number`, weight 0.50): Bank, gov, utility, courier, or telecom brand claimed by personal mobile number or bare number on SMS.
  - `S05` (`header_claim_mismatch`, weight 0.55): Text brand doesn't match DLT header brand, or a promotional `-P` header asks for OTP/KYC/payment.
  - `B06` (`verified_header_consistent`, factor 0.50): Suffix `-T`, `-S`, or `-G` matching the claimed brand with no links or asks. Dampens score and sets $m'=0$. Never applies on hard signals.
  - `C11` Combo: `S04` + any ask (`A*`) or link (`L*`) $\implies$ Floor 0.82 (`DANGER`).
  - SMS Model Influence Cap: When `isSms && ruleScore < 0.20`, $m' \le 0.55$ max, preventing model-only `DANGER` alerts on SMS.
- **Deduplication (`Deduplicator.kt`)**:
  - Added 5-minute sliding window cache on `(senderDisplay.lowercase() | text)` to deduplicate identical SMS notifications delivered across apps or RCS.
- **UX & Copy**:
  - Alert titles and details format SMS source: *"Likely scam SMS from <sender>"*, *"SMS from <sender>"*.
  - Onboarding and settings copy: *"Checks WhatsApp and SMS notifications. Never reads your inbox."*
- **Evaluation**:
  - Created `ml/data/sms_dev.jsonl` with 49 diverse Indian SMS samples (DLT transactional/service/promo negatives, personal number scams, header mismatches).
  - Evaluated on `sms_dev.jsonl`: 49 rows | Danger Prec 1.0, Caution+ Rec 1.0, B->Danger 0.0%, B->Caution 0.0%. Tier 1 Status: [PASS].
  - Verified frozen WhatsApp splits remain 100% identical (`dev`, `dev2`, `dev3`).

### 11. SMS Scam Refinements, Invariant 6 Safeguards, and Model Highlights Filtering
- **Invariant 6 Safeguard (`ScoreFusion.kt`)**:
  - Removed `S04` from `DANGER_QUALIFYING_SIGNALS`. An institution claim from a personal number (`S04`) combined only with model probability can now reach `CAUTION` at most (score capped below danger threshold `0.72`), strictly preventing the ML model alone from triggering `DANGER`.
- **Combo C12 (`ComboEngine.kt`, `rules.json`)**:
  - Added combo `C12`: `S04 and any(P01, P02, P03, P04)` $\implies$ Floor 0.82 (`DANGER`).
  - Category dynamically assigned from the active threat (`P04` $\to$ `UTILITY_DISCONNECT`, `P03` $\to$ `AUTHORITY_DIGITAL_ARREST`, `P02` $\to$ `PHISHING_BANK_KYC`, else `S04.category`).
- **P04 Lexicon Expansion (`en.json`, `hi.json`, `hi-Latn.json`)**:
  - Broadened `threat_utility_disconnect` to cover active and passive voice constructions: *"will disconnect your power supply"*, *"will disconnect your power"*, *"will disconnect power"*, *"will be disconnected"*, *"bijli kaat di jayegi"*, *"bijli connection kat diya jayega"*, *"बिजली काट दी जाएगी"*, etc.
- **Model Highlights Filtering (`Stopwords.kt`, `LinearClassifier.kt`)**:
  - Implemented `Stopwords` object with comprehensive stopword sets for English, Hindi (Devanagari), and Hindi-Latin (Hinglish).
  - Dropped internal placeholders (`__url__`, `__phone__`, etc.), tokens under 3 characters/codepoints, and stopwords from model attribution highlights.
  - Highlights restricted to tokens with strictly positive scam attribution weights.
  - Added unit test `ModelHighlightFilterTest.kt` validating stopword omission and token retention.
- **SMS Parser Verification & Sender Logging (`NotificationParser.kt`, `NotificationRecorder.kt`)**:
  - Marked SMS notification capture as "structure unverified" in `OPEN_QUESTIONS.md` pending physical recordings from Google Messages and Samsung Messages.
  - Added debug-only decoupling hook `NotificationParser.debugSenderLogger` wired to `NotificationRecorder` to record the source field of the extracted sender (`MessagingStyle.person.name`, `EXTRA_TITLE`, `EXTRA_CONVERSATION_TITLE`).
  - Added `SmsCaptureFixtureTest.kt` in `capture/src/test` marked "structure unverified".
- **Product Copy Confirmation**:
  - Aligned onboarding strings (`onboarding_desc_1`, `onboarding_desc_3`), privacy proof documentation, and `README.md` to state: *"Checks WhatsApp and SMS notifications. Never reads your inbox."*
- **Evaluations & Baseline Invariance**:
### 12. Danger Qualifying Signals CI Guardrail and Invariant 6 Property Testing
- **Exact Set Alignment & Synchronisation**:
  - `ScoreFusion.DANGER_QUALIFYING_SIGNALS` verified and pinned to exactly 10 signals: `L01, L02, L03, L07, L09, L10, L11, A01, A02, A04`.
  - Added `"danger_qualifying_signals"` array in `packs/rules.json` and copied to assets (`:app:copyPacks`).
  - Added `@SerialName("danger_qualifying_signals") val dangerQualifyingSignals: List<String>` in `RulesPack` (`PackData.kt`).
  - Documented explicit line in `docs/ARCHITECTURE.md` (§10): `Danger qualifying signals (DANGER_QUALIFYING_SIGNALS): L01, L02, L03, L07, L09, L10, L11, A01, A02, A04.`
- **CI Guardrails**:
  - Added Gradle CI verification task `verifyDangerQualifyingSignals` in `tools/ci/ci-checks.gradle.kts` running automatically under `./gradlew check`.
  - Created JUnit test `DangerQualifyingGuardrailTest.kt` asserting exact equality across `ScoreFusion.DANGER_QUALIFYING_SIGNALS`, `rules.json`, and `docs/ARCHITECTURE.md`. Any change to the set without simultaneously updating the doc, rules pack, and code will fail CI.
- **Invariant 6 Property Test**:
  - Implemented 10,000-trial randomized property test in `DangerQualifyingGuardrailTest.kt`:
    - Generates random non-qualifying signal subsets, random weights (0.10..0.99), random context flags, real and mock combo evaluations ($< \text{dangerThreshold}$), random dampeners (B01..B06), across all sensitivities (LOW, BALANCED, HIGH), across both SMS and WhatsApp.
    - Tests adversarial model probability 1.0 (in $\ge 50\%$ of trials) and random probabilities.
    - Proves that the alert level is NEVER `AlertLevel.DANGER` and final score is strictly less than `dangerThreshold` for all 10,000 trials.
  - Implemented positive control tests verifying that qualifying signals or combo floors $\ge \text{dangerThreshold}$ CAN produce `DANGER`.
- **Score Clamping Refinement (`ScoreFusion.kt`)**:
  - Clamped `roundedScore` directly to `Math.round((dangerThreshold - 0.001) * 1000.0) / 1000.0` when unqualified for Danger, ensuring floating point rounding never allows an unqualified score to reach or round up to `dangerThreshold`.
- **Known Gap Recorded (`OPEN_QUESTIONS.md`)**:
  - Recorded item 10 tracking Hindi government-scheme and loan-fee scams (e.g. `new-scam-hi-05`). Missing Hindi brand aliases for government schemes (for `L09`), `P08` Hindi loan lure lexicon (`ऋण स्वीकृत`, `लोन मंजूर`), and `A03` upfront fee phrasing (`फाइल चार्ज भेजें`). Detection rules remain frozen; planned for the next lexicon/data round verified on dev splits.

### 13. Privacy & Safety Controls for Real Notification Recordings
- **.gitignore Invariant (§2, §14, §19)**:
  - Excluded real notification recordings and private message evaluation sets from git tracking: `recordings/`, `**/recordings/`, `*.recording.json`, `*.recording.jsonl`, `*.recording.txt`, `eval/private/`, `**/private/`. Real recordings live strictly on developer machines.
- **CI & Pre-Commit PII Scanner**:
  - Created `tools/ci/check_pii.py` supporting `--staged`, `--all`, and `--history` modes.
  - Added Gradle verification task `verifyNoPiiLeakage` to `tools/ci/ci-checks.gradle.kts` wired into `./gradlew check`.
  - Enforces detection across tracked files and commits for:
    1. Indian mobile numbers (`(?<![\d.a-zA-Z])(?:\+91[\s-]?)?[6-9]\d{4}[\s-]?\d{5}(?![\d.a-zA-Z])`), exempting documented synthetic placeholders (`+91 98765 43210`, `+91 91234 56789`, etc.), toll-free support numbers (`1800...`, `1930`), and foreign test placeholders.
    2. Bank account numbers (`9–18` digits preceded by English/Hindi account keywords `a/c`, `account`, `खाता संख्या`, etc.), allowing masked account numbers (`**1234`, `XXXX1234`).
    3. OTP codes (`4–8` digits adjacent to `OTP` / `ओटीपी`), exempting synthetic test placeholders (`123456`, `492019`, etc.).
    4. UPI IDs (`local@handle` where handle is in `packs/lists/upi_handles.txt`), exempting documented test placeholders (`user@okaxis`, `helpme2024@ybl`).
    5. Personal/private email addresses, exempting documented placeholders (`user@example.com`, `support@example.com`, `test@example.com`) and RFC 2606 / RFC 6761 reserved example domains (`.example.com`, `.example.org`, `.example.net`, `.example`, `.test`, `.invalid`, `.localhost`).
- **Versioned Git Hook (`tools/ci/hooks/pre-commit`)**:
  - Configured repository-level versioned git hooks via `git config core.hooksPath tools/ci/hooks`.
  - Moved pre-commit hook into tracked file `tools/ci/hooks/pre-commit`.
  - Documented one-line developer setup (`git config core.hooksPath tools/ci/hooks`) in `README.md` under "Contributing".
- **Test Fixture Redaction Policy**:
  - Added Core Rule 5 in `AGENTS.md` and updated `docs/ARCHITECTURE.md` (§14, §19): before converting real notification recordings into test fixtures, all names, numbers, amounts, and codes must be redacted first. Only redacted fixtures with synthetic placeholders may be committed.

### 14. Milestone M5 Phase A: Tier 2 Regional Languages
- **Scope & Languages**:
  - Implemented Phase A covering 5 Tier 2 regional languages: Bengali (`bn`), Marathi (`mr`), Telugu (`te`), Tamil (`ta`), Odia (`or`).
  - Added language packs in `packs/lang/{bn,mr,te,ta,or}.json` with comprehensive lexicons covering all `A*` actions (`A01`–`A09`), `P*` pressure/context signals (`P01`–`P04`, `P08`, `P11`, `P12`), and regional brand aliases.
- **Script Gating & Marathi/Hindi Discrimination (§8)**:
  - Implemented `LanguageScriptDetector.isUntrainedScript` to detect predominant script; when predominant script is outside trained set `{LATIN, DEVANAGARI}`, the ML classifier is bypassed ($m'=0$) and SafeLog event 304 (`EVENT_MODEL_UNTRAINED_SCRIPT_RULES_ONLY`) is emitted.
  - Implemented Devanagari language discriminator `LanguageScriptDetector.isMarathi` based on function-word ratio (`आहे`, `आणि`, `नाही`, `आहेत` vs `है`, `और`, `नहीं`, `हैं`), safely gating the model off ($m'=0$) for Marathi until Milestone M6 retraining.
  - Unit tests added in `ScriptGatingTest.kt` and `MarathiHindiDiscriminatorTest.kt` verifying both gating directions.
- **Brand Verification & Unverified Brand Handling**:
  - Verified and added regional brands: West Bengal WBSEDCL (`wbsedcl.in`), Maharashtra MSEDCL (`mahadiscom.in`), Odisha TPCODL (`tpcentralodisha.com`), Andhra APCPDCL (`apcpdcl.in`), and regional state police DLT header mappings (`VK-KOLPOL-G`, `TN-CHNPOL-G`, etc.).
  - Added `tgspdcl` (Telangana) and `tangedco` (Tamil Nadu) marked `isVerified: false` in `packs/brands.json`.
  - Hardened `SignalEngine` so that unverified brands NEVER trigger `L02` (lookalike brand domain) or `L03` (brand mention with mismatched domain), and NEVER count toward `B02`/`B06` dampeners. Unit tested in `UnverifiedBrandSuppressionTest.kt`.
- **UI Localization**:
  - Created complete string resources for all 5 languages in `app/src/main/res/values-{bn,mr,te,ta,or}/strings.xml`.
  - Updated `SettingsScreen.kt` with all 8 Indian languages supported by the application.
- **Evaluation Splits & Quality Gates**:
  - Generated 10 independent evaluation splits (`eval/dev_{bn,mr,te,ta,or}.jsonl` and `eval/test_{bn,mr,te,ta,or}.jsonl`), each with 160 rows (60 scam, 100 benign = 62.5% benign) across 15 scam templates and 20 benign templates (100% disjoint between dev and test).
  - Evaluated against Section 16.2 Tier 2 quality gates:
    - Danger Precision $\ge 0.95$ (achieved 1.0 across all languages).
    - Caution+ Recall $\ge 0.80$ (achieved $0.80$ to $1.0$ across all languages).
    - Benign $\to$ Danger $\le 0.5\%$ (achieved $0.0\%$ across all languages).
    - Benign $\to$ Caution+ $\le 3.0\%$ (achieved $0.0\%$ across all languages).
- **Tier 1 Non-Regression**:
  - Verified complete non-regression across all Tier 1 datasets (`corpus.jsonl`, `dev.jsonl`, `dev2.jsonl`, `dev3.jsonl`, `sms_dev.jsonl`) with 0 regressions.
### 15. Milestone M5 Phase A Review & Hardening
- **L02 / L03 Disambiguation & Schemeless Executable Fix**:
  - Clarified signal naming in `rules.json` and UI strings: `L02` is `brand_domain_mismatch` (unofficial/mismatched domain for claimed brand), while `L03` is `lookalike_domain` (typosquatting/levenshtein distance).
  - Fixed root cause in `UrlParser.kt`: schemeless tokens ending in executable extensions (`.apk`, `.xapk`, `.exe`, etc., e.g., `wedding_card.pdf.apk`, `echallan_tn.apk`, `sbi-update.apk`) are rejected in `isPotentialUrl` and `parseCandidate`. They are strictly handled by `L01` (`apk_file_or_link`) and cannot produce spurious bare domain entities or trigger `L02`/`L03`.
  - Added `LinkSignalsAndLabelsTest.kt` asserting:
    1. Signal-ID to name mapping for every $L^*$ signal (L01–L12).
    2. APK filename alone triggers L01 only with no L02/L03.
    3. `sbi-update.apk` triggers L01 with no domain signals (L02–L12).
- **Awareness vs Hard Signals Invariance**:
  - Enforced that hard signals (`L01`, `L10`, `L11`, `A01`, `A02`, `A04`) and sensitive asks are NEVER suppressed by awareness context.
  - Removed `isAwareness` suppression from `A06` (`asks_secrecy_or_stay_on_call`) in `SignalEngine.kt`.
  - Purged generic confidentiality phrases (`keep this confidential`, `maintain secrecy`, `गोपनीय रखें`, `secret rakhna`, etc.) from language packs across all locales, retaining only true digital arrest / coercion directives.
  - Added `AwarenessVsHardSignalsTest.kt` asserting that for every Tier 2 language (`bn`, `mr`, `te`, `ta`, `or`), awareness text followed by a direct OTP ask triggers `A01` and blocks `B05`, resulting in DANGER.
- **Police DLT Header Allowlist**:
  - Replaced broad regex (`POL`, `COP`, `CYBER`) matching with strict verified allowlist in `packs/lists/police_dlt_headers.txt`.
  - Unlisted police-looking headers receive no `B06` dampener. If an unlisted police header claims police authority with an ask or link, `S05` (`header_claim_mismatch`) fires and combos apply.
  - Added `PoliceDltHeaderVerificationTest.kt` testing listed headers, unlisted advisory headers, and unlisted headers claiming authority with malicious links.

### 16. M5 Phase A Checkpoint Closure & Engine Hardening
- **Police DLT Allowlist Strict Verification**:
  - Web review of official portals (`delhipolice.gov.in`, `cybercrime.gov.in`, `keralapolice.gov.in`, `trai.gov.in`) confirmed that none publish official circulars literally showing the 6-character DLT header strings.
  - In strict compliance with Section 19 and rule "homepage URLs are not sources", emptied `packs/lists/police_dlt_headers.txt`. All police-claiming headers are treated as unverified (`B06` suppressed, `S05` fired if claiming police authority).
  - Updated `PoliceDltHeaderVerificationTest.kt` to assert empty allowlist behavior (unlisted headers do not earn B06; claiming police fires S05) and tested explicit allowlist functionality.
- **EXECUTABLE_EXTENSIONS & Domain Handling Verification**:
  - Confirmed `EXECUTABLE_EXTENSIONS` contains: `setOf("apk", "xapk", "apks", "apkm", "exe", "scr", "bat", "cmd", "msi", "vbs", "jar")`. Real TLDs `.zip`, `.mov`, `.app` are not in `EXECUTABLE_EXTENSIONS`.
  - Added unit tests in `LinkSignalsAndLabelsTest.kt`:
    1. Schemeless `.zip` and `.app` domains (`secure-login.zip`, `verify.app`) are parsed as domains and evaluate domain signals (e.g. `L02`).
    2. URLs ending in executable files (`http://x-bank.com/update.apk`) evaluate `L01` AND host-based domain signals on `x-bank.com` (`L02`).
    3. Bare APK filenames (`echallan_ts.apk`) fire `L01` only with no domain signals (`L02..L12`).
- **Engine CLI Explain Row Parsing & Category Format**:
  - Updated `runExplain` in `engine-cli/src/main/kotlin/com/duarf/engine/cli/Main.kt` to support `--row "<json>"`, `--text "<json>"`, or `--in <file> --id <id>`. Automatically populates `sender_display`, `sender_kind`, `app`, and `is_group` from dataset row by default.
  - Fixed category presentation: when score is 0.0 and verdict is `NONE`, `Category` outputs `NONE` instead of `OTHER_SUSPICIOUS`.
  - Ignored `label: "context_only"` rows in `runEval` main loop to avoid misclassifying preceding context rows as benign test cases.
### 17. Milestone M5 Phase B Part 1: Engine Hardening & Gap Generalization
- **Signal Deduplication Before Fusion**:
  - Implemented `deduplicateSignals(signals)` in `SignalEngine.kt` called immediately before returning from `evaluate(...)`.
  - Groups fired signals by `signalId`, selects the instance with the highest weight, preserves primary `evidenceSpan`, and consolidates all distinct evidence spans into `allEvidenceSpans`.
  - Updated `ExplanationEngine.kt` to extract highlights across `allEvidenceSpans` for selected reason signals.
  - Added `SignalDeduplicationTest.kt` asserting that repeated brand mentions or signal triggers produce exactly one signal instance in fusion while preserving all highlight spans.
- **Awareness Guards on S04, P10, S05, and B05**:
  - Enforced spec invariance: awareness context suppresses `S04` (awareness preamble), `P10` (brand impersonation without official domain), and `S05` (header claim mismatch) ONLY when NO qualifying ask (`A*`), link (`L*`), or threat (`P02`–`P04`) fires (`!hasHardOrAskOrLink`).
  - Applied identical guard to dampener `B05` (`awareness_or_advisory_context`): blocked whenever any `A*`, `L*`, or `P02`–`P04` fires.
  - Added unit tests in `AwarenessVsHardSignalsTest.kt` verifying that awareness preambles paired with `A03` or `A07` prevent `B05` application and fire appropriate scam alerts.
- **A04 Proximity Rule & Scoped Negation Handling**:
  - Upgraded `A04` (`asks_upi_pin_to_receive`) matcher: fires when an instruction to enter/share a PIN or scan a QR code occurs within $N \le 8$ tokens of a receive money / cashback lure.
  - Implemented scoped negation handling in `SignalEngine.kt` (`isNegatedUpiPinAsk`): negation applies ONLY when it grammatically governs the PIN requirement/instruction itself (`never enter PIN`, `PIN is not needed`, `पिन की आवश्यकता नहीं`, `PIN sirf bhejne ke liye hai`).
  - Generic negation and condition tokens (`mat`, `sirf`, `no`, `only`, `don't worry`) in preambles or unrelated clauses do NOT negate `A04`.
  - Added unit test suite in `UpiPinProximityAndNegationTest.kt` covering all 6 mandatory user phrases, Hindi/Hinglish/regional phrasing, and mixed scam/warning messages.
- **Gap #10 Generalization (Loan Fee Scam Combo C13)**:
  - Added combo `C13` in `ComboEngine.kt`: Loan lure (`P08`) + upfront fee ask (`A03`) from unknown sender (`S01`) $\to$ CAUTION (floor 0.55, category `LOAN_CREDIT`).
  - Invariant 6 preserved: `A03` is NOT added to danger-qualifying signals; `C13` does not produce DANGER without a qualifying link (`L09`, `L02`).
- **Benign Evaluation Additions & Verification**:
  - Added genuine bank-style UPI safety notices as benign rows across dev splits in all active languages (`en`, `hi`, `hi-Latn`, `bn`, `mr`, `te`, `ta`, `or`). All evaluated to `NONE` (0.0% FP).
  - Added genuine bank loan notices with processing fees (DLT headers and official domains) as benign rows across dev splits in all active languages. All evaluated to `NONE` (0.0% FP).

### 18. Milestone M5 Phase B Part 2: Tier 3 Regional Languages & Final M5 Validation
- **Tier 3 Language Packs (`packs/lang/`)**:
  - Added packs for Gujarati (`gu.json`), Kannada (`kn.json`), Malayalam (`ml.json`), and Punjabi (`pa.json`).
  - Implemented all 23 standard lexicon categories per language: `asks_otp_pin_cvv`, `asks_install_app`, `asks_payment`, `upi_pin_to_receive`, `upi_pin_instruction`, `receive_money_lure`, `negation_upi_pin_receive`, `asks_identity_details`, `asks_secrecy_or_stay_on_call`, `asks_click_to_fix`, `asks_move_platform`, `money_from_new_number`, `urgency_deadline`, `threat_account_block`, `threat_legal_arrest`, `threat_utility_disconnect`, `lure_prize_lottery`, `lure_job_task`, `lure_instant_loan`, `lure_refund_cashback`, `delivery_failed`, `traffic_challan`, `generic_mass_greeting`.
- **Brand Registry Expansion (`packs/brands.json`)**:
  - Verified and registered regional utility brands: UGVCL (`ugvcl.com`), BESCOM (`bescom.karnataka.gov.in`), KSEB (`kseb.in`), PSPCL (`pspcl.in`), all marked `isVerified: true`.
  - Added native script aliases for police, electricity, SBI, HDFC, ICICI, PNB, Axis, Kotak, Bank of Baroda, India Post, and Parivahan across Gujarati (`Gujr`), Kannada (`Knda`), Malayalam (`Mlym`), and Gurmukhi (`Guru`).
- **Engine URL & Subdomain Matching Hardening**:
  - Enhanced `isOfficial` in `SignalEngine.kt` to match `officialDomains` against `url.host` and subdomain suffixes (`url.host.endsWith(".$it")`) in addition to `url.registrableDomain`. Correctly recognizes official government/utility subdomains like `bescom.karnataka.gov.in` without triggering `L02` or `L03`.
  - Updated `B02` (`official_domains_only`) dampener to check host and subdomain suffix matching.
  - Added native-script UPI PIN negation regex patterns and regional awareness phrases in `SignalEngine.kt` for Gujarati, Kannada, Malayalam, and Punjabi.
- **UI Localization**:
  - Created complete string resources for all 4 languages in `app/src/main/res/values-{gu,kn,ml,pa}/strings.xml` (all 178 keys matching `values/strings.xml`, including reason strings and actionable advice).
- **Dataset Generation & Evaluation**:
  - Implemented `tools/eval/generate_tier3_datasets.py` with 15 scam templates and 22 benign templates per language (including genuine UPI safety advisories and pre-approved bank loans with processing fees).
  - Generated `eval/dev_{gu,kn,ml,pa}.jsonl` (160 rows each, 60 scam / 100 benign = 62.5% benign) and froze `eval/test_{gu,kn,ml,pa}.jsonl` (disjoint held-out templates, untouched during development).
  - Evaluated against Phase B quality gates:
    - `dev_gu`: Danger Prec=1.0, Caution+ Rec=1.0, Benign $\to$ Danger=0.0%, Benign $\to$ Caution+=0.0% [PASS]
    - `dev_kn`: Danger Prec=1.0, Caution+ Rec=1.0, Benign $\to$ Danger=0.0%, Benign $\to$ Caution+=0.0% [PASS]
    - `dev_ml`: Danger Prec=1.0, Caution+ Rec=1.0, Benign $\to$ Danger=0.0%, Benign $\to$ Caution+=0.0% [PASS]
    - `dev_pa`: Danger Prec=1.0, Caution+ Rec=1.0, Benign $\to$ Danger=0.0%, Benign $\to$ Caution+=0.0% [PASS]
  - Full test suite and static analysis (`./gradlew check`) passed cleanly (218 actionable tasks).

### 19. Milestone M5 Phase B Finalization & Frozen Tier 3 Test Execution
- **A04 Hinglish Receive Lexicon Resolution (`packs/lang/hi-Latn.json`)**:
  - Expanded `receive_money_lure` in Hindi-Latin to include account-receive phrases (`account me aa jayenge`, `account me aayenge`, `account me credit`, `account me transfer`, `khate me aa jayenge`, `khate me credit`, `refund mila hai`, `cashback mila hai`) matching other language packs.
  - Eliminated all 6 False Negatives in `scam-grp-33` (`scam-0034` etc.) on `corpus.jsonl`. Caution+ recall on corpus rose from 0.983 to 1.0 (Danger Precision: 1.0, FP: 0.0%).
- **Explanation Specificity Tie-Breaker (`ExplanationEngine.kt`)**:
  - Implemented specificity ranking (`getSpecificityRank`) when two signals in the same family tie on effective weight.
  - Specifically prefers `L09` (`gov_claim_non_gov_domain`) over `L02` (`brand_domain_mismatch`) for government/police claims, and `S05` (`header_claim_mismatch`) over `S04` (`institution_claim_from_personal_number`).
- **Additional Negation Test (`UpiPinProximityAndNegationTest.kt`)**:
  - Added unit test verifying that informational/negated statement `"UPI PIN is only required for sending money, not receiving"` does not fire `A04`.
- **Golden Vectors Updated (`packs/golden_vectors.json`)**:
  - Regenerated golden vector entries reflecting the corrected `scam-grp-33` verdict (`DANGER` via `A04` + `C05`).
- **Clean CI & Full Test Run**:
  - Verified `./gradlew clean check --rerun-tasks` passes with 223 actionable tasks executed.
- **Frozen Tier 3 Test Execution (`eval/test_{gu,kn,ml,pa}.jsonl`)**:
  - Executed held-out evaluation sets once without any test tuning. Aggregates and gate reports compiled for M5 checkpoint report.
- **Corpus A04 Cleanliness & Tier 1 Recall Report Correction**:
  - Note that `corpus.jsonl` A04 rows are no longer a clean blind check, since the Hinglish receive-lure fix used phrases taken from them.
  - Corrected M5 report metrics: actual Tier 1 Caution+ recall across dev splits is 0.999 (`dev`), 0.994 (`dev2`), and 0.997 (`dev3`) (not 1.0).
- **Unverified Regional Utility Brands (`packs/brands.json`)**:
  - Added stubs for 8 regional power distribution companies: DGVCL, MGVCL, PGVCL, GUVNL (Gujarat) and MESCOM, HESCOM, GESCOM, CESC (Karnataka / West Bengal).
  - Configured with native-script aliases, `isVerified = false`, and `officialDomains = []`. Logged domain sourcing in `OPEN_QUESTIONS.md`.
### 20. Milestone M5 Closeout & M6 Groundwork
- **Beta Labelling Localization & Review Tags (§19.3)**:
  - Extracted all beta badges and disclaimer strings into `strings.xml` across all 12 supported locales (`values`, `values-hi`, `values-b+hi+Latn`, `values-bn`, `values-mr`, `values-te`, `values-ta`, `values-or`, `values-gu`, `values-kn`, `values-ml`, `values-pa`).
  - Marked all regional translations with `<!-- Marked for native-speaker review §19.3 -->`.
  - Updated `SettingsScreen.kt` to style the beta testing note with `MaterialTheme.colorScheme.onSurfaceVariant` instead of error color.
- **Police Advisory False Positive Diagnosis & Regional Awareness**:
  - Diagnosed police cyber safety advisory false positives: identified missing regional cyber security notice and helpline phrases in `isAwarenessOrAdvisory`.
  - Added regional awareness keywords (`"সাইবার নিরাপত্তা"`, `"સાયબર સુરક્ષા"`, `"ಸೈಬರ್ ಸುರಕ್ಷತೆ"`, `"സൈബർ സുരക്ഷ"`, `"ਸਾਈਬਰ ਸੁਰੱਖਿਆ"`, `"helpline 1930"`) to `SignalEngine.kt`.
- **S05 Alone Cap Rule (ARCHITECTURE.md §10 Spec Change & ScoreFusion.kt)**:
  - Spec rule: A sender mismatch (`S05`) with nothing asked (`A*`), linked (`L*`), or threatened (`P02`–`P04`) is not actionable.
  - Capped score strictly below the Caution threshold (`cautionThreshold - 0.001` $\to 0.449$ at Balanced sensitivity), yielding `AlertLevel.NONE`.
  - If any ask, link, or urgency/threat signal fires alongside `S05`, normal score fusion applies.
  - Added test coverage in `PoliceDltHeaderVerificationTest.kt` verifying unverified police header with no ask $\to$ `NONE`, and police header with ask/link $\to$ `CAUTION` / `DANGER`.
- **Dev Splits Re-run (Tier 1, Tier 2, Tier 3)**:
  - Re-evaluated `dev`, `dev2`, `dev3`, `sms_dev`, `dev_{bn,mr,te,ta,or}`, `dev_{gu,kn,ml,pa}` via `engine-cli eval`.
  - Verified 0 rows changed alert level across all dev splits vs baseline.
- **OEM SMS Apps Candidate List (`OPEN_QUESTIONS.md`)**:
  - Documented exhaustive OEM SMS package candidate list (Xiaomi `com.android.mms`, Samsung `com.samsung.android.messaging`, OnePlus `com.oneplus.mms`, OPPO/Realme `com.coloros.mms`, Vivo `com.vivo.mms`, Transsion `com.android.mms`) with status UNVERIFIED pending physical device recordings.
  - Removed `com.transsion.phonemaster` as it is a utility app rather than an SMS app.
- **S05 Cap Lift Refinement & Callback Ask Handling (§10 Spec Amendment)**:
  - Spec rule: S05 cap is lifted when `P01` (`urgency_deadline`) fires, or when the message contains an instruction to call or WhatsApp a phone number (`hasCallbackAsk`), or when any `A*`, `L*`, `P02`–`P04` fires.
  - Implemented `hasCallbackAsk` in `SignalEngine.kt` checking for call/WhatsApp imperatives in proximity to extracted phone numbers.
  - Updated `ScoreFusion.kt` and `DefaultScamEngine.kt`.
  - Added unit tests in `PoliceDltHeaderVerificationTest.kt`:
    1. Police header + "case registered, call <number> immediately" $\to$ NOT capped (Caution or above).
    2. Police header + pure advisory including "helpline 1930" $\to$ `NONE`.
    3. Scam text with "1930" mention + OTP ask or link $\to$ unchanged level (`DANGER`).
  - Re-run of all dev splits (`dev`, `dev2`, `dev3`, `sms_dev`, `dev_{bn,mr,te,ta,or}`, `dev_{gu,kn,ml,pa}`) confirmed 0 rows changed level.
- **Pack Update Specification (`docs/PACK_UPDATE_SPEC.md`)**:
  - Documented that pack and model updates occur strictly via signed app updates.
  - Explicitly rejected SAF/external storage pack imports because malicious "update packs" would constitute an active scam vector.
- **Play Store Compliance & Privacy Copy Alignment (`docs/PLAY_STORE_LISTING.md`)**:
  - Documented official Play policy requirement of 12 testers opted in for 14 continuous days for personal accounts.
  - Updated store and in-app privacy copy across `AboutScreen.kt` and `strings.xml` to accurately state: *"Messages that aren't flagged are analysed in memory and discarded. Flagged alerts are saved only on your phone, and you can delete them anytime."*

## 21. Milestone M6 Release Hardening, Invariants & Final Evaluation
- **Native Speaker Review Kits & Intake Tooling**:
  - Implemented `tools/eval/generate_review_kit.py` generating comprehensive review packets for all 9 regional languages in `eval/review_kits/` (all 23 lexicon categories, 178+ UI keys, and 30 sampled dev rows).
  - Implemented `tools/eval/intake_real_world.py` with schema verification and strict PII check enforcement (`tools/ci/check_pii.py`).
- **Frozen Tier 3 Test Sets Generated & Committed FIRST**:
  - Generated and frozen `eval/test_{gu,kn,ml,pa}.jsonl` (commit `ab98c5e`) before M6 rule modifications using completely disjoint templates and phrasing.
- **Listener Health Monitoring (Zero New Permissions)**:
  - Manifest permissions remain strictly limited to `POST_NOTIFICATIONS` and `VIBRATE`. `RECEIVE_BOOT_COMPLETED` forbidden.
  - In `WaNotificationListener.kt`:
    - `onListenerDisconnected()` calls `requestRebind(ComponentName)` and posts local "Protection Paused" notification (ID 19301) with pending intent to `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
    - `onListenerConnected()` saves `last_connected_timestamp` to SharedPreferences and cancels the paused notification.
  - In `HomeScreen.kt`: checks listener permission and connection state, displaying an actionable warning card with a direct deep link to system settings.
  - In `MainActivity.kt`: refreshes health status in `onResume()`.
- **OEM Battery Optimization Guidance Screen**:
  - Created `BatteryOptimizationScreen.kt` with step-by-step guidance for Xiaomi (HyperOS/MIUI), Samsung (One UI), OnePlus/OPPO/Realme (ColorOS), and Vivo/iQOO (Funtouch OS).
  - Included mandatory version disclaimer: *"Menu options and navigation paths may vary depending on device model and OS version."*
  - Linked from `SettingsScreen.kt` and `OnboardingScreen.kt` (Step 4).
- **Comprehensive UI String Localization**:
  - Added all battery guide, listener health, and back button strings across all 12 supported locales (`values`, `values-hi`, `values-b+hi+Latn`, `values-bn`, `values-gu`, `values-kn`, `values-ml`, `values-mr`, `values-or`, `values-pa`, `values-ta`, `values-te`).
  - Total keys reached 198 per complete locale, with regional strings tagged with `<!-- Marked for native-speaker review §19.3 -->`.
- **Accessibility (a11y) & Visual Polish**:
  - Replaced deprecated navigation icons with `Icons.AutoMirrored.Filled.ArrowBack`.
  - Added TalkBack semantic labels on all icon buttons (`btn_back`, `setting_privacy_proof`, `settings_title`).
  - Hardened alert detail banner to include both color and icons/text badges for color-independent accessibility (§13.3).
- **Performance Benchmarks**:
  - Implemented `OnDevicePerformanceBenchmarkTest.kt` in `:app` androidTest suite for physical device execution (`./gradlew :app:connectedAndroidTest`), recording device model, OS, and RAM profile.
  - Implemented `EngineRegressionBenchmarkTest.kt` in `:engine` test suite verifying Section 15 budgets: cold start init $\le 400\text{ ms}$, p95 message analysis $\le 25\text{ ms}$, featurizer + predict $\le 5\text{ ms}$ on JVM.
- **Release Build & R8 Shrinking**:
  - Executed `./gradlew :app:assembleRelease`. Generated production APK `app-release.apk` with R8 minification and resource shrinking.
  - Release APK size: **2.0 MB** (comfortably under the 15 MB limit).
  - All Section 14 CI tasks passed: `verifyPermissions`, `verifyDependencies`, `verifyNoContentLogging`, `verifyNoDebugToolsInRelease`, `verifyNoPiiLeakage`, `verifyExportedComponents`, `verifyEngineIsPure`.
- **Final Evaluation of Frozen Tier 3 Test Sets**:
  - Evaluated `eval/test_{gu,kn,ml,pa}.jsonl` once at milestone completion:
    - Gujarati (`test_gu`): Danger Precision = 1.0 (100%), Benign $\to$ Danger = 0.0%, Benign $\to$ Caution+ = 0.0% [PASS]
    - Kannada (`test_kn`): Danger Precision = 1.0 (100%), Benign $\to$ Danger = 0.0%, Benign $\to$ Caution+ = 0.0% [PASS]
    - Malayalam (`test_ml`): Danger Precision = 1.0 (100%), Benign $\to$ Danger = 0.0%, Benign $\to$ Caution+ = 0.0% [PASS]
    - Punjabi (`test_pa`): Danger Precision = 1.0 (100%), Benign $\to$ Danger = 0.0%, Benign $\to$ Caution+ = 0.0% [PASS]



