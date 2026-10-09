# Open Questions & Inputs (§19)

Items marked "verify" and Section 19 human inputs are tracked here.

1. **Real notification fixtures (§19.1)**: Debug `NotificationRecorder` implemented in `capture/src/debug` to record on-device WhatsApp notification extras. Real fixture for e-challan APK scam added in `EChallanApkCaptureFixtureTest.kt` (marked "structure unverified" pending WhatsApp recording verification).
2. **Real messages for evaluation (§19.2)**: `eval/real_world.jsonl` created tracking real-world cases (`origin: real`). E-challan APK scam added with scored document row ("RTO E challan.apk") and unscored image context row ("Photo"). Post-MVP item added for on-device image OCR.
3. **Native-speaker review of language packs (§19.3)**: Initial packs marked with `reviewedBy: null` ("beta" in UI).
4. **Verified official domains in `brands.json` (§19.4)**:
   - **Status**: Unverified brand domains are tracked and suppressed with `isVerified: false`.
   - **Current Unverified Brands**:
     1. `tgspdcl` / `tsspdcl`: Telangana Southern Power Distribution Company Ltd. Domains `tgspdcl.com` and `tssouthernpower.com` pending verification due to ongoing Telangana state renaming (TS to TG).
     2. `tangedco`: Tamil Nadu Generation and Distribution Corporation. Domains `tangedco.gov.in` and `tnebltd.gov.in` pending human verification due to discom corporate restructuring under TNEB Ltd.
     3. `dgvcl`: Dakshin Gujarat Vij Company Limited. Added with empty `officialDomains` (`[]`); official domains need sourcing and verification.
     4. `mgvcl`: Madhya Gujarat Vij Company Limited. Added with empty `officialDomains` (`[]`); official domains need sourcing and verification.
     5. `pgvcl`: Paschim Gujarat Vij Company Limited. Added with empty `officialDomains` (`[]`); official domains need sourcing and verification.
     6. `guvnl`: Gujarat Urja Vikas Nigam Limited. Added with empty `officialDomains` (`[]`); official domains need sourcing and verification.
     7. `mescom`: Mangalore Electricity Supply Company Limited. Added with empty `officialDomains` (`[]`); official domains need sourcing and verification.
     8. `hescom`: Hubli Electricity Supply Company Limited. Added with empty `officialDomains` (`[]`); official domains need sourcing and verification.
     9. `gescom`: Gulbarga Electricity Supply Company Limited. Added with empty `officialDomains` (`[]`); official domains need sourcing and verification.
     10. `cesc`: Chamundeshwari Electricity Supply Corporation (Karnataka) / Calcutta Electric Supply Corporation. Added with empty `officialDomains` (`[]`); official domains need sourcing and verification.
   - **Engine Guardrail**: Brands with `isVerified == false` never fire `L02` or `L03`, and cannot satisfy `B02` or `B06` (verified by `UnverifiedBrandSuppressionTest.kt`).
5. **Play developer account & signing keys (§19.5)**: Placeholder keystore and configuration used for release builds in local development.
6. **Final branding assets (§19.6)**: App icon chosen 2026-10-08: "Abhaya shield" (raised palm inside a shield, indigo #2F3A8F). Masters in `docs/brand/`; Android layers in `app/src/main/res/drawable/ic_launcher_*.xml` and `ic_stat_duarf.xml`. Still open: final app name (DUARF is a working name) and whether the icon needs a trademark check before the Play listing.
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
8. **License**: Resolved (GPL-3.0-or-later; bundled Public Suffix List subset under MPL-2.0, see `THIRD_PARTY_NOTICES.md`).
9. **SMS Notification Format Verification (§19)**:
   - **Status**: **Structure unverified**. No real-device notification recordings yet.
   - Google Messages (`com.google.android.apps.messaging`) and Samsung Messages (`com.samsung.android.messaging`) are monitored behind the "Check SMS" toggle. The parser extracts sender from `MessagingStyle.person.name` or `EXTRA_TITLE` and logs which field the sender originated from via `NotificationRecorder`. Real-world recordings from Google Messages and Samsung Messages will be provided to verify structure and extras key conventions.
   - **Candidate OEM SMS Apps (Unverified - Marked "verify")**:
     | OEM / Skin | Default SMS Package | Market Share / Context | Verification Status & Criteria |
     |---|---|---|---|
     | Xiaomi / Redmi / POCO (MIUI / HyperOS) | `com.android.mms` | Substantial Indian market share | **UNVERIFIED**: Needs physical device recording via `NotificationRecorder`; verify `MessagingStyle`, `EXTRA_TITLE`, and DLT header presentation |
     | Samsung (One UI) | `com.samsung.android.messaging` | Monitored in code | **UNVERIFIED**: Real physical notification capture required to verify extras key stability |
     | Google Messages (Pixel, Motorola, Nothing) | `com.google.android.apps.messaging` | Monitored in code | **UNVERIFIED**: Real physical notification capture required |
     | OnePlus (OxygenOS) | `com.oneplus.mms` (legacy) / `com.google.android.apps.messaging` | Common in India | **UNVERIFIED**: Legacy package needs recording; modern devices default to Google Messages |
     | OPPO / Realme (ColorOS / Realme UI) | `com.coloros.mms`, `com.heytap.mms` | Substantial Indian market share | **UNVERIFIED**: Real physical notification capture required |
     | Vivo / iQOO (Funtouch OS) | `com.vivo.mms`, `com.android.mms` | Substantial Indian market share | **UNVERIFIED**: Real physical notification capture required |
     | Transsion (Tecno / Infinix / Itel) | `com.android.mms` | Common budget tier in India | **UNVERIFIED**: Real physical notification capture required |
   - **Enforcement Rule**: No candidate package may be added to active monitoring until a real physical notification recording is captured, verified, and committed as a sanitized test fixture (§19.1).
10. **Known gap: Hindi government-scheme / loan-fee scams (rules frozen)**:
    - **Observed Behavior**: Messages matching templates like `new-scam-hi-05` (e.g. *"प्रधानमंत्री मुद्रा योजना के तहत ₹5,00,000 का ऋण 1% ब्याज पर स्वीकृत हुआ है। फाइल चार्ज ₹21015 इस लिंक http://... पर भेजें।"*) currently fire only `L06` (risky TLD) and `S01`/`S03` (unknown number), landing on borderline scores.
    - **Missing Elements**:
      1. Hindi brand aliases for government schemes (PM Mudra / प्रधानमंत्री मुद्रा योजना, PM Kisan / पीएम किसान, Ayushman Bharat / आयुष्मान भारत, etc.) in `brands.json` so `L09` (`gov_claim_non_gov_domain`) can fire when paired with non-governmental links.
      2. `P08` Hindi loan lure lexicon (`ऋण स्वीकृत`, `लोन मंजूर`, `ब्याज पर स्वीकृत`).
      3. `A03` upfront fee phrasing (`फाइल चार्ज भेजें`, `प्रोसेसिंग फीस भेजें`, `फाइल चार्ज इस लिंक पर भेजें`).
    - **Status**: Partially addressed in M5 Phase A. `pm_mudra` brand added to `brands.json`, enabling `L02` and `L09` on PM Mudra scams paired with non-gov links. This resolved all 28 rows of `new-scam-hi-05` in `dev3.jsonl` (reducing dev3 FN from 32 to 4, lifting dev3 recall from 0.973 to 0.997). Remaining general `P08` and `A03` Hindi phrasing additions remain queued for M6.

11. **Post-MVP candidate: Image check via share-to-check + on-device OCR (no media permission)**:
    - User shares an image/screenshot to DUARF via the Android share sheet. On-device OCR extracts text and runs detection without requiring broad media or storage permissions.
    - Canonical example: E-challan APK scam where violation notice text is embedded in an image sent alongside an APK.
    - Distinguish from automatic background scanning of received images in messaging apps, which is a separate opt-in candidate idea.

12. **Verified Police and Law Enforcement DLT SMS Headers (§19.4)**:
    - **Status**: **Unverified**. No official government notice, TRAI circular, or police gazette notification literally publishing 3-6 character DLT entity header strings (e.g. `DLPOL`, `NCRP`, `KLPOL`) is available on official government portals (`delhipolice.gov.in`, `cybercrime.gov.in`, `keralapolice.gov.in`, `trai.gov.in`).
    - Homepages are not sources and header strings cannot be inferred from naming patterns. `packs/lists/police_dlt_headers.txt` is kept empty until official circulars literally showing the header strings are obtained.
    - **Engine Guardrail**: Unlisted police-claiming headers never receive `B06` (police advisory dampener), and if paired with an ask or link, fire `S05` (`header_claim_mismatch`). Tested in `PoliceDltHeaderVerificationTest.kt`.

13. **Tier 3 Regional Language Test Split Retirement (§19.2, §19.3)**:
    - **Observed Metrics on Held-out Tier 3 Test Splits (`eval/test_{gu,kn,ml,pa}.jsonl`)**:
      - Caution+ Recall was 0.667–0.800 across splits:
        - Gujarati (`gu`): 0.667 (20 FN / 60)
        - Kannada (`kn`): 0.800 (12 FN / 60)
        - Malayalam (`ml`): 0.667 (20 FN / 60)
        - Punjabi (`pa`): 0.733 (16 FN / 60)
      - Benign → Caution: 5.0% across all 4 languages (5 rows per language in template family `test-<lang>-ben-tpl-06`, genuine cyber safety notice from state police DLT header with `S05`).
      - Danger Precision: 1.0 (0.0% FP to Danger).
    - **Failing Categories**: `AUTHORITY_DIGITAL_ARREST`, `JOB_TASK`, `LOTTERY_PRIZE`.
    - **Spent Test Sets**: The existing Tier 3 test splits are now spent and unblinded. No tuning or fixes may be made against these rows. Any M6 fixes will need a new frozen test split generated from disjoint templates.


