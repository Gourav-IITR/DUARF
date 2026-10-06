# Open Questions & Inputs (§19)

Items marked "verify" and Section 19 human inputs are tracked here.

1. **Real notification fixtures (§19.1)**: Debug `NotificationRecorder` implemented in `capture/src/debug` to record on-device WhatsApp notification extras. Real fixture for e-challan APK scam added in `EChallanApkCaptureFixtureTest.kt` (marked "structure unverified" pending WhatsApp recording verification).
2. **Real messages for evaluation (§19.2)**: `eval/real_world.jsonl` created tracking real-world cases (`origin: real`). E-challan APK scam added with scored document row ("RTO E challan.apk") and unscored image context row ("Photo"). Post-MVP item added for on-device image OCR.
3. **Native-speaker review of language packs (§19.3)**: Initial packs marked with `reviewedBy: null` ("beta" in UI).
4. **Verified official domains in `brands.json` (§19.4)**: Unverified, needs human check against cited sources and verification dates.
5. **Play developer account & signing keys (§19.5)**: Placeholder keystore and configuration used for release builds in local development.
6. **Final branding assets (§19.6)**: Placeholder vector assets used in MVP.
