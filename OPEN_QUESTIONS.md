# Open Questions & Inputs (§19)

Items marked "verify" and Section 19 human inputs are tracked here.

1. **Real notification fixtures (§19.1)**: Debug `NotificationRecorder` implemented in `capture/src/debug` to record on-device WhatsApp notification extras. Real fixture for e-challan APK scam added in `EChallanApkCaptureFixtureTest.kt` (marked "structure unverified" pending WhatsApp recording verification).
2. **Real messages for evaluation (§19.2)**: `eval/real_world.jsonl` created tracking real-world cases (`origin: real`). E-challan APK scam added with scored document row ("RTO E challan.apk") and unscored image context row ("Photo"). Post-MVP item added for on-device image OCR.
3. **Native-speaker review of language packs (§19.3)**: Initial packs marked with `reviewedBy: null` ("beta" in UI).
4. **Verified official domains in `brands.json` (§19.4)**: Unverified, needs human check against cited sources and verification dates.
5. **Play developer account & signing keys (§19.5)**: Placeholder keystore and configuration used for release builds in local development.
6. **Final branding assets (§19.6)**: Placeholder vector assets used in MVP.
7. **Known gap after M4: obfuscated scams (adversarial recall 0.50; hi 0.886)**:
   - Evaluated on frozen test split: Devanagari Hindi recall was 0.886 < 0.90, failing the Tier 1 per-language gate due to 47 misses on adversarial template `fresh-scam-adv-02`. Overall adversarial recall was 0.50.
   - **Obfuscation technique in `fresh-scam-adv-02` (by type only)**:
     - Lexical substitution / synonym evasion (descriptive Indic paraphrasing of credential tokens, bypassing primary keyword matching).
     - Security awareness pretext wrapping / negation pretexting (embedding harvesting instructions inside security warning text with negation clauses).
   - **M6 Hardening Plan**:
     1. **Paraphrase coverage for A01 (`asks_otp_pin_cvv`) in hi/hi-Latn/en lexicons**: Add descriptive terms for codes and credentials (e.g. "सत्यापन कोड", "गुप्त कोड", "verification code", "secret code", "security PIN") built strictly from dev data, not from test templates.
     2. **Tighten "do not share" negation logic**: Negation may ONLY suppress A01 when:
        - The message contains NO request for the user to reply, send, read out, or confirm a code, AND
        - The sender is NOT `NUMBER_ONLY` (genuine OTP deliveries do not originate from unknown consumer numbers).
        - Add dev-set adversarial cases of security-warning-wrapped OTP requests.
     3. **Normalizer improvements**: Enhance `TextNormalizer` to handle homoglyphs, spacing/delimiters, and mixed scripts across Indic/Latin.
     - *Evaluation protocol*: Evaluated exclusively on dev splits (`dev`, `dev2`, `dev3`). Frozen test split re-runs strictly deferred until the M6 checkpoint.

