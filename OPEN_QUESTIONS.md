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
     - Improve the text normalizer to detect and handle obfuscations (homoglyphs, spacing, mixed scripts, Devanagari orthographic variants).
     - Add these obfuscation techniques to synthetic train and dev generators.
     - Evaluate exclusively on dev splits (`dev`, `dev2`, `dev3`).
     - Frozen test split re-runs strictly deferred until the M6 checkpoint.
