# Third-Party Notices and Licenses

This file documents third-party data and software components included in or distributed with DUARF.

---

## 1. Third-Party Data in `packs/`

### Public Suffix List (`packs/lists/psl.dat`)
- **Origin / Source:** Mozilla Public Suffix List ([https://publicsuffix.org/](https://publicsuffix.org/))
  Source file: [https://publicsuffix.org/list/public_suffix_list.dat](https://publicsuffix.org/list/public_suffix_list.dat)
- **License:** Mozilla Public License, Version 2.0 (MPL-2.0)
- **License Terms:**
  ```text
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at https://mozilla.org/MPL/2.0/.
  ```
  The subset of suffixes in `packs/lists/psl.dat` is provided under MPL-2.0 and is not licensed under GPL-3.0.

---

## 2. DUARF Original Packs & Data (`packs/`)

The following files in `packs/` were authored and created specifically by the DUARF project and are licensed under **GPL-3.0-or-later**:

- `packs/rules.json`: Detection heuristic rules, feature weights, combo definitions, and invariant thresholds.
- `packs/brands.json`: Curated database of legitimate Indian banks, telecom providers, government portals, utilities, and courier services.
- `packs/golden_vectors.json`: Test vector fixtures for parity and regression verification.
- `packs/lang/*.json`: Curated language lexicons (urgency keywords, financial terms, action phrases) across 12 Indic scripts and Latin transliteration (`bn`, `en`, `gu`, `hi`, `hi-Latn`, `kn`, `ml`, `mr`, `or`, `pa`, `ta`, `te`).
- `packs/lists/police_dlt_headers.txt`: Curated list of verified police/cybercrime TRAI DLT SMS headers.
- `packs/lists/remote_apps.txt`: Curated list of remote-desktop application package and tool names.
- `packs/lists/risky_tlds.txt`: Curated list of high-abuse top-level domains.
- `packs/lists/shorteners.txt`: Curated list of URL shortening services.
- `packs/lists/upi_handles.txt`: Curated list of NPCI-registered UPI PSP handle suffixes.
- `packs/lists/blocklist.txt`: User-extensible local domain blocklist.
- `packs/model/model.bin` & `packs/model/model.json`: Quantized linear classifier weights and Platt calibration parameters trained using `ml/train.py`.

---

## 3. Runtime Third-Party Software Dependencies

All runtime dependencies distributed in binary form with DUARF are open-source and licensed under GPL-3.0-compatible licenses.

Every runtime dependency below is licensed under the **Apache License, Version 2.0 (Apache-2.0)**:

### AndroidX & Jetpack Compose
- `androidx.activity:*` (Version 1.9.3) — Apache-2.0
- `androidx.annotation:*` (Version 1.8.1) — Apache-2.0
- `androidx.arch.core:*` (Version 2.2.0) — Apache-2.0
- `androidx.autofill:autofill` (Version 1.0.0) — Apache-2.0
- `androidx.collection:*` (Version 1.4.4) — Apache-2.0
- `androidx.compose.animation:*` (Version 1.7.5) — Apache-2.0
- `androidx.compose.foundation:*` (Version 1.7.5) — Apache-2.0
- `androidx.compose.material3:*` (Version 1.3.1) — Apache-2.0
- `androidx.compose.material:*` (Version 1.7.5) — Apache-2.0
- `androidx.compose.runtime:*` (Version 1.7.5) — Apache-2.0
- `androidx.compose.ui:*` (Version 1.7.5) — Apache-2.0
- `androidx.compose:compose-bom` (Version 2024.10.01) — Apache-2.0
- `androidx.concurrent:concurrent-futures` (Version 1.1.0) — Apache-2.0
- `androidx.core:*` (Version 1.15.0) — Apache-2.0
- `androidx.customview:*` (Version 1.0.0) — Apache-2.0
- `androidx.datastore:*` (Version 1.1.1) — Apache-2.0
- `androidx.emoji2:emoji2` (Version 1.3.0) — Apache-2.0
- `androidx.fragment:fragment` (Version 1.5.1) — Apache-2.0
- `androidx.graphics:graphics-path` (Version 1.0.1) — Apache-2.0
- `androidx.hilt:*` (Version 1.2.0) — Apache-2.0
- `androidx.interpolator:interpolator` (Version 1.0.0) — Apache-2.0
- `androidx.lifecycle:*` (Version 2.8.7) — Apache-2.0
- `androidx.loader:loader` (Version 1.0.0) — Apache-2.0
- `androidx.navigation:*` (Version 2.8.3) — Apache-2.0
- `androidx.profileinstaller:profileinstaller` (Version 1.3.1) — Apache-2.0
- `androidx.room:*` (Version 2.6.1) — Apache-2.0
- `androidx.savedstate:*` (Version 1.2.1) — Apache-2.0
- `androidx.sqlite:*` (Version 2.4.0) — Apache-2.0
- `androidx.startup:startup-runtime` (Version 1.1.1) — Apache-2.0
- `androidx.tracing:tracing` (Version 1.2.0) — Apache-2.0
- `androidx.versionedparcelable:versionedparcelable` (Version 1.1.1) — Apache-2.0
- `androidx.viewpager:viewpager` (Version 1.0.0) — Apache-2.0

### Kotlin & Coroutines & Serialization
- `org.jetbrains.kotlin:kotlin-stdlib*` (Version 2.0.21) — Apache-2.0
- `org.jetbrains.kotlinx:kotlinx-coroutines-*` (Version 1.9.0) — Apache-2.0
- `org.jetbrains.kotlinx:kotlinx-serialization-*` (Version 1.7.3) — Apache-2.0
- `org.jetbrains:annotations` (Version 23.0.0) — Apache-2.0

### Dependency Injection & Utilities
- `com.google.dagger:hilt-android` / `dagger` (Version 2.52) — Apache-2.0
- `com.google.code.gson:gson` (Version 2.8.9) — Apache-2.0
- `com.google.code.findbugs:jsr305` (Version 3.0.2) — Apache-2.0
- `com.google.guava:listenablefuture` (Version 1.0) — Apache-2.0 (via parent POM)
- `com.squareup.okio:okio*` (Version 3.4.0) — Apache-2.0
- `jakarta.inject:jakarta.inject-api` (Version 2.0.1) — Apache-2.0
- `javax.inject:javax.inject` (Version 1) — Apache-2.0
