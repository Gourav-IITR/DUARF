# DUARF Pack & Model Update Specification (§9, §17 Milestone M6)

## 1. Overview
DUARF operates with **zero network permissions** (`android.permission.INTERNET` is strictly forbidden by Section 2 invariants). Consequently, rule packs (`packs/rules.json`), brand lists (`packs/brands.json`), language lexicons (`packs/lang/*.json`), indicator lists (`packs/lists/*.txt`), and the int8 ML model (`packs/model/model.bin`) cannot be downloaded directly over the network by the application at runtime.

---

## 2. Update Mechanism: Signed App Updates Only

All pack, list, and model updates are delivered exclusively through **signed application updates** distributed via the Google Play Store or official cryptographic GitHub APK releases.

### 2.1 Rationale Against External File / SAF Pack Import
An alternative design considered allowing users to import updated packs from device storage using the Android Storage Access Framework (SAF / `ACTION_OPEN_DOCUMENT`). **This design was evaluated and explicitly rejected for security reasons:**

> **Scam Attack Vector Prevention**:
> Allowing arbitrary file imports for detection packs introduces a critical social engineering vulnerability:
> 1. A scammer impersonating customer support, bank security, or cyber police could convince a victim to download a malicious "DUARF Security Patch Pack" or "Cyber Update file" (`duarf_update.json` / `.bin`) via WhatsApp or a phishing website.
> 2. If imported by the victim, the malicious pack could disable detection rules, set weights to zero, whitelist the scammer's phishing domains/DLT headers as verified, or suppress alerts for specific phone numbers.
> 3. Even with cryptographic signatures, managing public keys and revoking compromised keys entirely offline without network access is structurally fragile.

By restricting pack updates strictly to OS-level signed APK installations:
- Android's cryptographic package signature verification (`apksigner`, APK Signature Scheme v2/v3) guarantees authenticity and integrity.
- Malicious third parties cannot manipulate detection rules without compromising the Google Play Console developer signing keys.

---

## 3. Bundled Pack Packaging & Integrity Verification

### 3.1 APK Asset Structure
Packs are bundled inside the APK assets directory:
```
assets/
└── packs/
    ├── rules.json              # Versioned signal weights, thresholds, combos
    ├── brands.json             # Brand definitions, categories, official domains, DLT tokens
    ├── lists/
    │   ├── remote_apps.txt     # Known remote-access tool keywords
    │   ├── blocklist_domains.txt
    │   ├── police_dlt_headers.txt # Strictly verified police headers only
    │   └── public_suffix_list.dat # PSL for registrable domain extraction
    ├── lang/
    │   ├── en.json             # English lexicons
    │   ├── hi.json             # Hindi lexicons (Devanagari)
    │   ├── hi-Latn.json        # Hinglish lexicons (Latin)
    │   ├── bn.json             # Bengali lexicons
    │   ├── mr.json             # Marathi lexicons
    │   ├── te.json             # Telugu lexicons
    │   ├── ta.json             # Tamil lexicons
    │   ├── or.json             # Odia lexicons
    │   ├── gu.json             # Gujarati lexicons
    │   ├── kn.json             # Kannada lexicons
    │   ├── ml.json             # Malayalam lexicons
    │   └── pa.json             # Punjabi lexicons
    └── model/
        ├── model.bin           # 262,176-byte int8 quantized weights + CRC32
        └── model.json          # Training provenance, parameters, metrics
```

### 3.2 Loading and Integrity Checks
1. **CRC32 Checksum Validation**:
   - `LinearClassifier` checks the CRC32 checksum embedded in the `model.bin` footer during cold startup. If the checksum does not match, the model is rejected and the engine fails safe to pure rules-only detection.
2. **Schema & Version Validation**:
   - `PackLoader` validates the version schema of `rules.json`. If incompatible, the application logs a safe diagnostic event and falls back to bundled baseline rules.
3. **Immutability**:
   - Assets are read-only at runtime, stored in the application's signed APK container, preventing local tampering or unauthorized modification by other apps.
