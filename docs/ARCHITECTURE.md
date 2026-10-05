# On-device WhatsApp scam detector for Android: MVP architecture

Working name: **Pehredar** (Hindi for "sentinel"). The name, package id and branding are placeholders.
Spec version 1.0, written 6 October 2026. Audience: a coding agent building the app end to end, and the human reviewing its work.

---

## 0. How to use this document

Build in the milestone order of section 17. Each milestone has acceptance criteria; do not start the next until the current one passes. Section 2 lists invariants that override everything else in this document: if an instruction elsewhere appears to conflict with an invariant, the invariant wins and the conflict should be reported to the human.

Where this spec gives a number (weights, thresholds, budgets), treat it as the starting value. Where it says "verify", the fact could not be confirmed from documentation and must be checked on a real device or against the live source before it is relied on.

Section 19 lists inputs only the human can supply. Stub them with clearly marked placeholders and keep going; do not invent real-looking data to fill them.

### Decisions already made

| Decision | Choice | Consequence |
|---|---|---|
| Platform | Android only, native Kotlin | No cross-platform framework |
| Capture | Notification access plus manual share/paste | No accessibility service, no overlay on WhatsApp |
| Detection | Deterministic rules plus a small ML classifier | No on-device LLM in the MVP |
| Network | App ships without the `INTERNET` permission | Blocklists and models update only via app updates; no analytics or crash SDKs |
| Languages | English, Hindi, Hinglish plus regional languages | Language support is data-driven (packs), tiered by quality bar |
| Distribution | Google Play first | Target API 36; sideloading is a secondary path |

---

## 1. Product summary and scope

The app watches incoming WhatsApp message notifications, scores each message on the phone for scam likelihood, and posts its own warning notification with plain-language reasons when a message looks dangerous. The user can also share or paste any message into the app to check it. Nothing the app reads ever leaves the device.

### In scope for the MVP

- Automatic analysis of incoming notifications from WhatsApp (`com.whatsapp`) and WhatsApp Business (`com.whatsapp.w4b`).
- Manual check via Android share sheet, text-selection menu, and an in-app paste box.
- Two alert levels (Caution, Danger) with up to three reasons each, highlighted evidence, and category-specific advice.
- Local alert history with user-controlled retention and one-tap wipe.
- Feedback buttons ("This is safe" / "This is a scam") that adjust local behaviour only.
- Languages per section 8.

### Out of scope for the MVP

- iOS, WhatsApp Web, Telegram, SMS (the `MessageSource` interface in section 5 leaves room for them).
- Accessibility service, screen overlays, reading chat history, reading media contents, OCR of images.
- Any cloud component: accounts, sync, telemetry, remote config, live URL reputation.
- Conversation-level scams that have no single tell-tale message (romance, long-con investment grooming).
- Outgoing-message analysis.
- On-device LLM explanations.

---

## 2. Invariants (non-negotiable)

1. **No network capability.** The release app's merged manifest must not contain `android.permission.INTERNET`, `ACCESS_NETWORK_STATE` or `ACCESS_WIFI_STATE`. CI fails the build otherwise (section 14).
2. **No dependency that phones home.** No Firebase, Google Play Services, ML Kit, analytics, crash-reporting or ad SDKs. CI enforces a forbidden-group list.
3. **Benign messages leave no trace.** Text of a message scored below the Caution threshold is never written to disk, logs, or any persistent store. Only salted hashes and counters may persist.
4. **No message text in logs**, in any build type. Debug tooling that records notifications is a separate, explicitly enabled debug-only feature.
5. **WhatsApp is never modified, automated or impersonated.** The app does not cancel, snooze, reply to or alter WhatsApp's notifications, and does not use WhatsApp's name or logo in its own name or icon.
6. **Every Danger alert carries at least one concrete, rule-derived reason.** The ML model alone can raise at most a Caution.
7. **The detection engine is pure Kotlin/JVM** with no Android imports, so it runs identically in the app, in unit tests and in the evaluation CLI.
8. **One featurizer.** The code that turns text into model features exists only in Kotlin. Python training consumes features exported by the Kotlin CLI, so training and inference cannot drift.
9. **Outgoing intents never carry message content** unless the user has explicitly triggered a share and seen the exact payload.

---

## 3. Tech stack

| Area | Choice | Notes |
|---|---|---|
| Language | Kotlin (latest stable 2.x) | Coroutines and Flow for async |
| UI | Jetpack Compose, Material 3 | Single-activity, Navigation Compose |
| SDK levels | `minSdk 26`, `targetSdk 36`, `compileSdk 36` | Play requires API 36 for new apps and updates from 31 Aug 2026 |
| DI | Hilt (KSP) | Engine module stays DI-free; wired in `:app` |
| Storage | Room, DataStore (Preferences) | Flagged-message text encrypted per section 12 |
| Serialization | kotlinx.serialization (JSON) | For packs and model metadata |
| ML inference | Hand-written pure Kotlin linear model | No native libraries, so no 16 KB page-size work and a small APK |
| ML training | Python 3.11+, scikit-learn | Lives in `/ml`, never shipped |
| Build | Gradle Kotlin DSL, version catalog (`libs.versions.toml`) | Use latest stable versions at build time and pin them |
| Tests | JUnit, Truth, Robolectric, Compose UI test, AndroidX Test | Engine tests are plain JVM |
| Static checks | Android Lint, Detekt, ktlint | Plus custom CI checks in section 14 |

No WebView, no native code, no reflection-heavy libraries in the MVP.

---

## 4. System architecture

```
 WhatsApp notification        Share sheet / text selection        Paste box
          |                              |                            |
          v                              v                            v
 +-------------------+        +--------------------+        +----------------+
 | NotificationSource|        | ShareSource        |        | ManualSource   |
 | (listener service)|        | (ACTION_SEND etc.) |        | (in-app)       |
 +---------+---------+        +---------+----------+        +--------+-------+
           \___________________________ | ___________________________/
                                        v
                              IncomingMessage (in RAM)
                                        |
                                        v
 +-----------------------------------------------------------------------+
 |  :engine  (pure Kotlin, no Android, no I/O except pack loading)        |
 |                                                                       |
 |  Normalizer -> EntityExtractor -> SignalEngine (rules + lexicons)     |
 |                                 \-> Featurizer -> LinearClassifier    |
 |                                          |               |            |
 |                                          v               v            |
 |                                      ScoreFusion  <------+            |
 |                                          |                            |
 |                                          v                            |
 |                                   Verdict + Reasons + Highlights      |
 +-----------------------------------------------------------------------+
                                        |
                 level NONE             |            level CAUTION / DANGER
        discard text, bump counters <---+---> persist encrypted alert,
                                              post warning notification,
                                              show in history
```

### Repository layout

```
pehredar/
  AGENTS.md                 pointer file for coding agents -> docs/ARCHITECTURE.md
  docs/ARCHITECTURE.md      this document
  app/                      Android app: UI, DI, manifest, notifications
  capture/                  Android library: listener service, parsers, share entry points
  engine/                   pure Kotlin/JVM library: the whole detection pipeline
  data/                     Android library: Room, DataStore, crypto, repositories
  engine-cli/               JVM CLI: featurize, eval, explain (used by CI and /ml)
  packs/                    source of truth for rules, brands, lexicons, lists, model
    rules.json
    brands.json
    lang/<code>.json
    lists/ (psl.dat, shorteners.txt, risky_tlds.txt, upi_handles.txt, remote_apps.txt, blocklist.bin)
    model/ (model.bin, model.json)
  ml/                       Python: dataset build, training, export
  eval/                     JSONL corpora, provenance notes
  tools/ci/                 manifest, dependency and log checks
```

`packs/` is copied into `app/src/main/assets/packs/` at build time by a Gradle task, and read straight from disk by `engine-cli`. The engine loads packs through a small `PackSource` interface so it does not know which.

### Module dependency rules

`:engine` depends on nothing but the Kotlin stdlib and kotlinx.serialization. `:capture` and `:data` depend on `:engine` for types only. `:app` depends on all three. `:engine-cli` depends on `:engine` only. Enforce this with Gradle; a dependency from `:engine` on any `android.*` or `androidx.*` artifact is a build failure.

---

## 5. Capture layer (`:capture`)

### 5.1 Common types (defined in `:engine`)

```kotlin
enum class SourceKind { NOTIFICATION, SHARE, PASTE }
enum class SourceApp { WHATSAPP, WHATSAPP_BUSINESS, UNKNOWN }
enum class SenderKind { NUMBER_ONLY, NAMED, UNKNOWN }

data class IncomingMessage(
    val fingerprint: String,          // SHA-256, see 5.4
    val source: SourceKind,
    val app: SourceApp,
    val conversationKey: String?,     // HMAC of a stable chat id; null for SHARE/PASTE
    val senderDisplay: String?,       // as shown in the notification; RAM only unless alert persists
    val senderKind: SenderKind,
    val senderCountryCode: String?,   // "+91", "+92", ... when senderKind == NUMBER_ONLY
    val isGroup: Boolean,
    val text: String,
    val attachmentHint: String?,      // e.g. a document file name surfaced in the notification
    val receivedAtMillis: Long,
)

interface MessageSource { val messages: Flow<IncomingMessage> }
```

### 5.2 Notification listener

Declare one `NotificationListenerService` (`WaNotificationListener`), protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`, with the standard intent filter. It needs no foreground service; the system binds it.

Processing rules in `onNotificationPosted`:

1. Return immediately unless `sbn.packageName` is a monitored package (user-toggleable set, default both WhatsApp packages; in debug builds also the app's own package, for the fake poster in section 16).
2. Skip if `FLAG_GROUP_SUMMARY` is set, if the notification category is `CATEGORY_CALL`, or if it is ongoing.
3. Parse with `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification`. If that returns null, fall back to `EXTRA_TITLE` plus `EXTRA_BIG_TEXT`/`EXTRA_TEXT`, then `EXTRA_TEXT_LINES`.
4. From a messaging style, emit one `IncomingMessage` per message whose sender is not the device user (a null sender `Person` means "self"). Use each message's own timestamp.
5. Hand off to a single-threaded coroutine dispatcher through a bounded channel (capacity 64, drop oldest). `onNotificationPosted` runs on the main thread and must do no parsing work beyond step 1.

WhatsApp re-posts a chat's notification with the accumulated unread messages every time a new one arrives, so the same message is seen repeatedly. Deduplication (5.4) handles this.

Implement `onListenerConnected` (optionally sweep `activeNotifications` once to catch up) and `onListenerDisconnected` (call `requestRebind`). Expose listener health (enabled, connected, time of last event, never content) to the home screen.

Derivations, all to be confirmed against real fixtures (section 19, item 1):

| Field | Derivation |
|---|---|
| `conversationKey` | HMAC-SHA256(install key, first non-null of `notification.shortcutId`, `sbn.tag`, conversation title) |
| `isGroup` | `EXTRA_IS_GROUP_CONVERSATION`, else messaging style's `isGroupConversation` |
| `senderDisplay` | Message `Person.name`; for one-to-one chats falls back to the notification title |
| `senderKind` | `NUMBER_ONLY` if the display matches a phone-number pattern (digits, spaces, `+`, `-`, brackets, 8+ digits), else `NAMED`. A name does not prove a saved contact, since business accounts show names too; verify how group participants not in contacts are displayed |
| `attachmentHint` | A file-name-looking token in the text (WhatsApp shows document names in notifications; verify) |

Known platform behaviours to design around:

- Android 15 and later redact notifications the system classifies as sensitive (one-time passwords) for listeners that lack a system-only permission. The app will see placeholder text for those. Nothing to do except not crash and not alert on it.
- Muted chats, and messages that arrive while that chat is open, produce no notification and are invisible to the app.
- Long messages may be truncated in the notification; treat the text as possibly partial.
- WhatsApp in a work profile, "dual apps" clone or second user space is not visible to a listener in the main profile.
- Aggressive OEM battery managers can unbind the listener. Detect staleness and show guidance; do not request battery-optimisation exemptions.

### 5.3 Share and paste entry points

- `CheckMessageActivity` with intent filters for `ACTION_SEND` (`text/plain`) and `ACTION_PROCESS_TEXT`. It runs the same pipeline and shows the result screen directly, whatever the score ("Looks safe" is a valid result here).
- An in-app paste box on the home screen.
- For both, `senderKind = UNKNOWN`, `conversationKey = null`, and sender-based signals are simply absent. Offer an optional toggle "This came from a number I don't know".

### 5.4 Deduplication and short context

- `fingerprint = SHA-256(conversationKey | senderDisplay | text | messageTimestamp)`. Keep an in-memory LRU of the last 500 fingerprints, plus a persisted table of fingerprints for alerted messages so an alert is never posted twice across process restarts.
- Scammers often split a message ("Dear customer..." then the link). Keep a RAM-only buffer per `conversationKey` holding up to the last 3 messages from the same sender within 5 minutes, evicted after 10 minutes. The engine receives the current message plus this context; signals found only in context count at half weight (section 10).

---

## 6. Detection pipeline (`:engine`)

```kotlin
interface ScamEngine {
    fun analyze(message: IncomingMessage, context: List<IncomingMessage> = emptyList()): Verdict
}

enum class AlertLevel { NONE, CAUTION, DANGER }

data class Verdict(
    val level: AlertLevel,
    val score: Double,               // fused, 0..1
    val ruleScore: Double,
    val modelProbability: Double?,   // null if model unavailable
    val category: ScamCategory,
    val reasons: List<Reason>,       // ordered by weight, max 3 shown
    val highlights: List<TextSpan>,  // offsets into the ORIGINAL text
    val engineVersion: String,       // packs version + model version
)

data class Reason(
    val signalId: String,
    val titleKey: String,            // string-resource key, localized in :app
    val detailKey: String,
    val args: Map<String, String>,   // e.g. domain, brand, file name
    val evidence: TextSpan?,
)
```

The engine is synchronous, stateless per call, thread-safe after initialisation, and never throws on input: any internal failure degrades to a rules-only or `NONE` verdict with an error counter incremented.

Guards: cap analysed text at 4,000 characters (record `truncated`), and bound total analysis time at 250 ms; on timeout return the rules computed so far.

### 6.1 Normalizer

Produces `normalized` text plus an index map back to original offsets (needed for highlights).

1. Unicode NFKC.
2. Remove invisible characters (U+200B, U+2060, U+FEFF, soft hyphen, bidi controls). Keep ZWJ/ZWNJ when both neighbours are Indic-script letters, since they are meaningful there; remove them elsewhere. Record `hadInvisibleChars`.
3. Fold Cyrillic and Greek look-alike letters to Latin inside tokens that are otherwise Latin; record `hadMixedScriptToken`.
4. Collapse letter-spaced words ("K Y C", "O.T.P") and common digit-for-letter swaps ("0TP") into a parallel "deobfuscated" view used only by lexicon matching.
5. Map Indic digits to ASCII digits. Lowercase Latin text.
6. Collapse whitespace.

### 6.2 Entity extractor

Runs on normalized text and returns typed entities with spans.

| Entity | What to capture |
|---|---|
| URL | Scheme-less and bare domains too; de-obfuscate `hxxp`, `[.]`, `(dot)`, spaces around dots. Parse host, registrable domain (via bundled public-suffix list), TLD, path, port, userinfo, punycode flag, IP-literal flag, file extension |
| File name | Tokens ending in `.apk`, `.xapk`, `.apks`, `.apkm`, `.exe`, `.scr`; flag double extensions such as `invite.pdf.apk` |
| Phone | Indian mobiles (optional `+91`/`0`, ten digits starting 6-9) and international numbers with country code |
| UPI id | `local@handle` where `handle` is in `lists/upi_handles.txt`; distinguish from e-mail (no dotted TLD) |
| Amount | `₹`, `Rs`, `INR`, `rupees` and regional equivalents, with lakh/crore words |
| Code | 4-8 digit numbers near an OTP/PIN/code keyword |
| Brand | Dictionary match from `brands.json` (names and aliases in all supported scripts) |

`brands.json` entries look like this. Every domain must carry a source and a verification date; do not ship unverified entries.

```json
{
  "id": "sbi",
  "kind": "BANK",
  "names": ["sbi", "state bank of india", "एसबीआई", "स्टेट बैंक"],
  "officialDomains": ["sbi.co.in", "onlinesbi.sbi"],
  "source": "https://sbi.co.in",
  "verifiedOn": "YYYY-MM-DD"
}
```

Seed the dictionary with major banks, UPI apps, telecom operators, couriers, e-commerce sites and the government bodies scammers impersonate (income tax, UIDAI, EPFO, Parivahan/e-Challan, India Post, TRAI, RBI, CBI, ED, customs, state electricity boards). Kinds: `BANK`, `PAYMENTS`, `GOVERNMENT`, `LAW_ENFORCEMENT`, `TELECOM`, `COURIER`, `ECOMMERCE`, `UTILITY`.

### 6.3 Signal engine

Section 7 defines it. Output: a list of fired signals, each with weight, evidence span and reason arguments.

### 6.4 Featurizer and classifier

Section 9 defines them. Output: a calibrated probability and per-token attribution.

### 6.5 Fusion and explanation

Sections 10 and 11.

---

## 7. Rules: signals, combos and lexicons

### 7.1 How rules are expressed

`packs/rules.json` declares signals and combos; code implements only the *detectors* they reference. A signal is one of three detector types:

- `LEXICON`: fires when any phrase from a named lexicon intent matches (lexicons live in language packs, section 8).
- `ENTITY`: fires on a property of extracted entities, implemented in Kotlin and referenced by name (for example `url.lookalikeOfBrand`).
- `CONTEXT`: fires on sender or conversation facts (for example `sender.numberOnly`).

```json
{
  "id": "L03",
  "name": "lookalike_domain",
  "detector": { "type": "ENTITY", "ref": "url.lookalikeOfBrand" },
  "weight": 0.60,
  "category": "PHISHING_BANK_KYC",
  "reason": { "title": "reason_lookalike_title", "detail": "reason_lookalike_detail" }
}
```

All phrase lexicons from all language packs are compiled into a single Aho-Corasick automaton at start-up, with Unicode-aware word-boundary checks. Matching therefore needs no language identification and costs one pass over the text regardless of how many languages are installed. Regular expressions are allowed only for entity extraction and must be linear-time in practice (no nested quantifiers); the 4,000-character cap is the backstop.

### 7.2 Signal catalogue (starting weights)

Sender and context:

| Id | Name | Fires when | Weight |
|---|---|---|---|
| S01 | sender_number_only | Sender shows as a bare phone number | 0.10 |
| S02 | sender_foreign_number | S01 and country code is not +91 | 0.15 |
| S03 | first_contact | No earlier message seen from this conversation key | 0.10 |

Links and files:

| Id | Name | Fires when | Weight |
|---|---|---|---|
| L01 | apk_file_or_link | An APK-type file name or a URL whose path ends in one | 0.55 |
| L02 | brand_domain_mismatch | A brand is named and a URL is present whose registrable domain is not one of that brand's official domains | 0.50 |
| L03 | lookalike_domain | Domain label within Damerau-Levenshtein distance 2 of an official domain label, or contains a brand name inside a non-official domain (`sbi-kyc-update.xyz`, `hdfcbank.secure-login.com`) | 0.60 |
| L04 | ip_literal_url | Host is an IP address | 0.35 |
| L05 | url_shortener | Domain is in `shorteners.txt` | 0.20 |
| L06 | risky_tld | TLD is in `risky_tlds.txt` | 0.15 |
| L07 | punycode_or_mixed_script_domain | `xn--` label or mixed scripts in host | 0.45 |
| L08 | obfuscated_url | URL needed de-obfuscation to parse | 0.35 |
| L09 | gov_claim_non_gov_domain | Government or law-enforcement brand named, URL not under `.gov.in` or `.nic.in` | 0.50 |
| L10 | url_userinfo_trick | URL has a userinfo part (`https://sbi.co.in@evil.example`) | 0.55 |
| L11 | blocklisted_domain | Registrable domain in bundled blocklist | 0.90 |
| L12 | redirect_to_other_chat | Link to `wa.me`, `t.me` or similar from a number-only sender | 0.20 |

Asks:

| Id | Name | Fires when | Weight |
|---|---|---|---|
| A01 | asks_otp_pin_cvv | Request to share or forward an OTP, PIN, CVV or password | 0.60 |
| A02 | asks_install_app | Request to install an app, including remote-access tools from `remote_apps.txt` | 0.55 |
| A03 | asks_payment | Request to pay a fee, fine, charge, deposit or advance | 0.35 |
| A04 | upi_pin_to_receive | Claims you must enter a UPI PIN, scan a QR or approve a request in order to receive money | 0.65 |
| A05 | asks_identity_details | Request for Aadhaar, PAN, card or account numbers | 0.35 |
| A06 | asks_secrecy_or_stay_on_call | "Do not tell anyone", "stay on the video call", "do not disconnect" | 0.45 |
| A07 | asks_click_to_fix | "Click the link to update / verify / reactivate" | 0.20 |
| A08 | asks_move_platform | "Message me on Telegram", "join this group" from a number-only sender | 0.20 |
| A09 | money_from_new_number | "This is my new number" or an urgent loan request from a number-only sender | 0.40 |

Pressure and lures:

| Id | Name | Fires when | Weight |
|---|---|---|---|
| P01 | urgency_deadline | "Within 24 hours", "tonight 9:30 pm", "immediately" | 0.20 |
| P02 | threat_account_block | Account, card, SIM or KYC blocked, suspended or expiring | 0.35 |
| P03 | threat_legal_arrest | Arrest warrant, FIR, court summons, parcel seized, money-laundering case | 0.50 |
| P04 | threat_utility_disconnect | Electricity, gas or connection will be cut | 0.45 |
| P05 | lure_prize_lottery | Lottery, lucky draw, prize won | 0.40 |
| P06 | lure_job_task | Part-time job, pay per like/review/task, daily earnings | 0.40 |
| P07 | lure_investment | Guaranteed or doubled returns, stock-tip groups, IPO allotment | 0.40 |
| P08 | lure_instant_loan | Instant or pre-approved loan with no documents | 0.25 |
| P09 | lure_refund_cashback | Refund, cashback or reward points about to expire | 0.30 |
| P10 | impersonates_institution | A `BANK`, `GOVERNMENT`, `LAW_ENFORCEMENT`, `TELECOM`, `COURIER` or `UTILITY` brand named by a number-only sender | 0.20 |
| P11 | delivery_failed | Parcel undeliverable, address update needed | 0.30 |
| P12 | traffic_challan | Traffic fine or e-challan pending | 0.25 |
| P13 | invitation_lure | Wedding or event invitation "card" to open or download | 0.10 |
| P14 | generic_mass_greeting | "Dear customer / user / sir-madam" | 0.10 |

Text hygiene:

| Id | Name | Fires when | Weight |
|---|---|---|---|
| T01 | hidden_or_homoglyph_text | Normalizer removed invisible characters or folded look-alike letters | 0.30 |

Dampeners (reduce the score; never applied when L01, L10, L11, A01, A02 or A04 fired):

| Id | Name | Fires when | Factor |
|---|---|---|---|
| B01 | otp_delivery_only | Message delivers a code, tells the reader not to share it, and has no URL or ask | 0.40 |
| B02 | official_domains_only | Every URL's registrable domain is on a brand allow-list | 0.30 |
| B03 | established_conversation | Named sender and 20+ earlier messages seen from this conversation key | 0.15 |
| B04 | user_trusted_sender | User marked this conversation as trusted | 0.50 |

### 7.3 Combos

Combos set a *floor* on the rule score and choose the category. They capture the patterns that should alert regardless of wording.

| Combo | Condition | Floor | Category |
|---|---|---|---|
| C01 | L01 and any of P10, P12, P13, P02, P09 | 0.92 | MALICIOUS_APK |
| C02 | L01 and S01 | 0.85 | MALICIOUS_APK |
| C03 | A01 and any of P10, P02, S01 | 0.85 | OTP_ACCOUNT_TAKEOVER |
| C04 | P03 and any of A06, A03, P10 | 0.88 | AUTHORITY_DIGITAL_ARREST |
| C05 | A04 | 0.80 | UPI_PAYMENT_FRAUD |
| C06 | P04 and any of A02, A03, P01, or any `L*` signal | 0.85 | UTILITY_DISCONNECT |
| C07 | L02 or L03 or L09, and any of P02, A07, A05 | 0.88 | PHISHING_BANK_KYC |
| C08 | P06 and any of A08, A03, L12 | 0.75 | JOB_TASK |
| C09 | P07 and any of A08, L12, A03 | 0.75 | INVESTMENT_TRADING |
| C10 | A02 (remote-access app) and any of P10, P02, P09 | 0.90 | REMOTE_ACCESS |

### 7.4 Categories

`MALICIOUS_APK`, `PHISHING_BANK_KYC`, `AUTHORITY_DIGITAL_ARREST`, `UTILITY_DISCONNECT`, `UPI_PAYMENT_FRAUD`, `OTP_ACCOUNT_TAKEOVER`, `REMOTE_ACCESS`, `JOB_TASK`, `INVESTMENT_TRADING`, `LOTTERY_PRIZE`, `LOAN_CREDIT`, `DELIVERY_COURIER`, `IMPERSONATED_CONTACT`, `OTHER_SUSPICIOUS`.

If no combo fired, the category is that of the highest-weight fired signal; if only the model fired, `OTHER_SUSPICIOUS`. Each category has its own advice block (section 13.3).

---

## 8. Languages

### 8.1 Why regional coverage is affordable

Most high-precision signals do not depend on language at all: APK files, look-alike and mismatched domains, shorteners, UPI ids, sender type, and combos built on them. Language-specific work is confined to phrase lexicons for the `A*` and `P*` intents, brand aliases in each script, reason-text translations, and evaluation data. Adding a language is therefore adding a pack and test data, with no code change.

### 8.2 Language pack format (`packs/lang/<code>.json`)

```json
{
  "code": "hi-Latn",
  "displayName": "Hinglish",
  "script": "Latn",
  "tier": 1,
  "reviewedBy": null,
  "lexicons": {
    "asks_otp_pin_cvv": ["otp bata", "otp bhej", "otp share kar", "pin batao", "code bhejo"],
    "urgency_deadline": ["turant", "abhi ke abhi", "aaj raat", "24 ghante"],
    "threat_utility_disconnect": ["bijli kat", "bijli connection kaat", "light kat jayegi"]
  }
}
```

Each lexicon key matches a `LEXICON` detector reference. Romanized packs must list common spelling variants, since transliteration is not standardised; the character n-gram model in section 9 covers what the lists miss. `reviewedBy` stays null until a native speaker signs off (section 19, item 3), and the app's language screen marks unreviewed languages as "beta".

### 8.3 Tiers

| Tier | Languages | Bar for the MVP |
|---|---|---|
| 1 | English (`en`), Hindi (`hi`), Hinglish (`hi-Latn`) | Full lexicons for every `A*`/`P*` intent, localized UI and reasons, metric gates in section 16 |
| 2 | Bengali (`bn`), Marathi (`mr`), Telugu (`te`), Tamil (`ta`), Odia (`or`) | Lexicons for every `A*` intent and P01-P04, P11, P12; brand aliases in the script; localized reasons; at least 150 labelled evaluation messages each; relaxed gates |
| 3 | Gujarati (`gu`), Kannada (`kn`), Malayalam (`ml`), Punjabi (`pa`) | Lexicons for A01-A04, P02-P04; reasons fall back to the user's UI language; reported but ungated metrics |

Romanized variants (`bn-Latn`, `ta-Latn` and so on) are added only where evaluation data shows they matter.

Reasons are shown in the app's UI language, chosen by the user, never inferred from the message. UI language uses per-app locales (`AppCompatDelegate.setApplicationLocales`).

---

## 9. ML classifier

### 9.1 Why a linear n-gram model

The MVP model is a logistic regression over hashed character and word n-grams. It was chosen over a small transformer because it runs in under a millisecond on any phone, adds about 256 KB to the APK, needs no native library, trains on a laptop CPU in minutes, copes well with romanized and deliberately misspelled text through character n-grams, and gives token-level attributions for free. Its weakness is shallow understanding of meaning, which the rules partly cover. The `Classifier` interface lets a distilled multilingual transformer replace it later without touching the rest of the pipeline.

```kotlin
interface Classifier {
    val version: String
    fun score(features: SparseVector): Double            // calibrated probability
    fun attribute(features: FeaturizedText): List<TokenAttribution>
}
```

### 9.2 Featurizer v1 (Kotlin only)

Input is the normalized text with entities replaced by placeholder tokens: `__url__`, `__phone__`, `__upi__`, `__amount__`, `__code__`, `__email__`, `__file_apk__`, `__brand_bank__`, `__brand_government__` and so on by brand kind. Placeholders stop the model memorising specific domains or numbers.

Tokens are split on Unicode whitespace and punctuation; placeholders stay whole; tokens longer than 30 code points are truncated.

| Family | Feature string | Notes |
|---|---|---|
| Word unigram | `w\|<token>` | |
| Word bigram | `b\|<t1> <t2>` | |
| Char n-gram | `c\|<gram>` | n = 3, 4, 5 over code points of `^token$`; not generated for placeholders |
| Meta | `m\|<name>` | Sender kind, group flag, scripts present, URL count bucket (0, 1, 2+), length bucket, each URL's TLD, shortener flag, entity-presence flags |

Do not feed rule signal ids into the model. Keeping the two independent means the model adds evidence instead of echoing the rules.

Hashing: MurmurHash3 x86 32-bit, seed 0, over the UTF-8 bytes of the feature string; index = hash AND (2^18 - 1). Each distinct active index has value 1, then the vector is scaled by 1/sqrt(k) where k is the number of distinct active indices.

The featurizer also returns, for each token, the indices it produced, so attribution can map weights back to text spans.

### 9.3 Training pipeline (`/ml`, never shipped)

1. `engine-cli featurize --in data.jsonl --out data.svm` produces LIBSVM-format features using the one Kotlin featurizer.
2. Python trains `LogisticRegression(penalty="elasticnet", solver="saga", class_weight="balanced")`, tuning `C` and `l1_ratio` on the dev split.
3. Splits are grouped by `group_id` so that variants generated from one template never straddle train and test. Ungrouped splits will report inflated metrics.
4. Fit Platt scaling on the dev split and export its two parameters.
5. Quantise weights to int8 (`scale = max|w| / 127`) and write `model.bin` and `model.json`.
6. `engine-cli eval` re-scores the frozen test split through the full engine and must reproduce Python's probabilities to within 0.01, proving the export.

The build environment may use the internet (to fetch public datasets, for example). The no-network invariant applies to the app.

### 9.4 Model file

`model.bin`, little-endian: magic `PHRD`, format version (u16), featurizer version (u16), log2 bucket count (u8), 3 reserved bytes, scale (f32), bias (f32), calibration A (f32), calibration B (f32), then 2^18 int8 weights, then CRC32 of everything before it.

Inference: `z = bias + scale * sum(w[i] * x[i])`, `p = sigmoid(A * z + B)`.

`model.json` records versions, training-set hash, training date, per-language metrics and the thresholds they were measured at. The engine checks CRC and featurizer version at load; on any mismatch it runs rules-only and the About screen says so.

### 9.5 Data

Corpus rows are JSONL:

```json
{"id":"...","text":"...","label":"scam","category":"MALICIOUS_APK","lang":"hi-Latn",
 "sender_kind":"NUMBER_ONLY","is_group":false,"origin":"synthetic","group_id":"tpl-0142","notes":""}
```

`origin` is `real`, `public` or `synthetic`. Sources:

- Public SMS spam and smishing datasets (for example the UCI SMS Spam Collection). Check each licence, record it in `eval/SOURCES.md`, and expect them to be English-heavy and dated.
- Scam templates per category and language in `ml/templates/`, expanded with slot filling (brands, amounts, domains, deadlines) and augmentation (shorteners, look-alike domains, homoglyphs, letter spacing, spelling variants). A language model may be used at development time to paraphrase and translate, but every language needs a native-speaker spot check before its data counts toward a gate.
- **Hard negatives**, which matter more than extra scam samples for keeping false alarms down: genuine bank debit and credit alerts, real OTP deliveries, courier updates, bill reminders, real wedding invitations, job posts shared by friends, "send me 500" between friends, festival forwards, group chatter, messages that mention scams in order to warn about them.
- Real messages collected by the human (section 19, item 2), held out entirely from training as the real-world test set.

Target sizes for the MVP: about 20,000 training rows with roughly 60% benign, a frozen test split of 3,000 or more, and whatever real-world set the human can supply. Be clear in `model.json` and the README that metrics on a mostly synthetic corpus overstate field performance.

---

## 10. Score fusion

Inputs: fired signals with weights `w_i` (halved for signals found only in context messages), dampener factors `d_j`, combo floors, and the model probability `m` (or none).

```
r0 = 1 - PRODUCT(1 - w_i)                       noisy-OR of positive signals
r1 = r0 * PRODUCT(1 - d_j)                      skipped if a hard signal fired
r  = max(r1, highest combo floor)               combo floors without a hard signal
                                                are ignored when B04 fired
m' = 0.8 * clamp((m - 0.5) / 0.5, 0, 1)         0 if the model is unavailable
score = 1 - (1 - r) * (1 - m')
```

Hard signals are L01, L10, L11, A01, A02 and A04.

A *concrete* signal is any fired `L*`, `A*`, `P*` or `T*` signal with weight 0.20 or more. If none fired, clamp `score` to just below the Danger threshold. This implements invariant 6.

In group chats the `S*` signals and P10 are disabled, because unknown numbers are normal there.

Levels, by the user's sensitivity setting:

| Sensitivity | Caution at | Danger at |
|---|---|---|
| Low | 0.55 | 0.80 |
| Balanced (default) | 0.45 | 0.72 |
| High | 0.35 | 0.65 |

All weights, floors and thresholds live in `rules.json`, are versioned with the pack, and are tuned on the dev split in milestone M4. Unit tests pin the fusion arithmetic with worked examples, including: one weak signal stays `NONE`; L01 alone from a named sender is `CAUTION`; L01 from a number-only sender is `DANGER`; a genuine OTP delivery is `NONE`; model at 0.99 with no concrete signal is `CAUTION`.

---

## 11. Explanations

1. Sort fired positive signals by effective weight. If a combo fired, its member signals come first.
2. Collapse signals in the same family so three link problems do not crowd out an ask. Show at most three reasons; the detail screen can expand to all.
3. Each reason renders a localized title and detail from string resources, filled with arguments from the signal. Examples in English:

| Signal | Title | Detail |
|---|---|---|
| L03 | Link imitates {brand} | This link goes to {domain}. {brand}'s real site is {officialDomain}. |
| L01 | Sends an app file to install | "{file}" is an Android app, not a document. Apps sent over chat can take control of your phone. |
| A01 | Asks for your OTP or PIN | No bank, company or government office asks for these over chat. |
| P03 | Threatens arrest or legal action | There is no such thing as a "digital arrest". Police do not arrest, question or collect money over chat or video call. |
| A04 | Says you must enter your PIN to receive money | You never need a PIN, QR scan or approval to receive money. |
| Model only | Wording matches known scam messages | The highlighted phrases are common in scam messages. |

4. Highlights are the evidence spans of the shown reasons plus, when the model contributed (`m' > 0.2`), up to five tokens with the highest positive attribution.
5. Copy rules: say what was observed and why it matters, name the specific domain or file, never state certainty ("Likely scam", not "This is a scam"), and keep reading level simple. Every string goes through `strings.xml`; no user-facing text is hard-coded in `:engine`.

---

## 12. Data model and storage (`:data`)

| Store | Contents | Sensitive fields |
|---|---|---|
| Room `alerts` | id, fingerprint (unique), createdAt, level, score, category, sourceKind, app, senderDisplay, text, reasonsJson, highlightsJson, engineVersion, userFeedback, feedbackAt, dismissed | senderDisplay, text and reasonsJson are stored as AES-256-GCM ciphertext |
| Room `conversation_stats` | conversationKey (HMAC), firstSeenAt, lastSeenAt, messageCount, trusted | None in clear; the key is a keyed hash |
| Room `suppressed_fingerprints` | fingerprint, createdAt | Hash only |
| Room `daily_counters` | day, messagesChecked, cautions, dangers | Counts only |
| DataStore | Settings: sensitivity, monitored apps, group alerts, retention days, UI language, onboarding state | None |

Crypto: one AES-256-GCM key and one HMAC-SHA256 key, both generated in and never leaving the Android Keystore, with no user-authentication requirement (the listener must write while the device is locked). A fresh random 12-byte IV per encrypted value, stored alongside the ciphertext. Use the platform `javax.crypto` APIs; no third-party crypto library.

Retention: alerts are deleted after the chosen period (7, 30 or 90 days; default 30). Purge on app start and on every alert insert, so no background scheduler is needed. "Delete all data" clears every table and both keys.

Backups: `android:allowBackup="false"` and data-extraction rules that exclude everything from cloud backup and device transfer.

What is deliberately not stored: any text or sender of a message below the Caution threshold, contact lists, phone numbers in clear outside encrypted alert rows, and any per-message record for benign traffic beyond the `conversation_stats` counters.

---

## 13. App UX (`:app`)

### 13.1 Onboarding

1. What the app does, in two sentences.
2. The privacy promise: this app has no internet permission, and how to verify that in system settings.
3. Prominent disclosure, then notification access. State plainly that the app will read the content of WhatsApp notifications, why, and that it stays on the device. Only after the user taps "Continue" open `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` for the listener component (API 30+) or `ACTION_NOTIFICATION_LISTENER_SETTINGS`. Detect the grant with `NotificationManagerCompat.getEnabledListenerPackages`.
4. `POST_NOTIFICATIONS` runtime permission (API 33+), explained as "so we can warn you".
5. Optional battery guidance for OEMs known to kill listeners. Instructions only.
6. "Send a test alert" runs a canned scam message through the real pipeline so the user sees what a warning looks like.

Use `<queries>` entries for the two WhatsApp packages to detect whether they are installed; this needs no permission.

### 13.2 Warning notification

Two channels: "Scam alerts" (high importance, heads-up) for Danger and "Cautions" (low importance, silent) for Caution. Title "Likely scam from {sender}" or "Be careful with a message from {sender}"; body is the top reason; expanded view lists up to three. Actions: "See why" and "Not a scam". Lock-screen visibility is private with a public version reading "Possible scam message detected". One notification per alerted message, grouped under a summary when there are several.

Target: the warning appears within one second of WhatsApp's notification.

### 13.3 Screens

| Screen | Contents |
|---|---|
| Home | Protection status (listener enabled and connected, time of last check), this week's counts, recent alerts, "Check a message" paste box |
| Alert detail | Level banner, the message with highlighted spans, reasons with evidence, category advice, feedback buttons, "Trust this sender", "Share this warning" |
| Check result | Same layout as alert detail, including a "Looks safe, stay alert" state for low scores |
| History | Alerts by date, filter by level, swipe to delete |
| Settings | Language, sensitivity, monitored apps, alert in groups, retention, trusted senders, delete all data |
| Privacy proof | The app's permissions read live from `PackageManager`, a plain explanation of each, and steps to confirm the absence of network access in system settings |
| About | Pack and model versions, model status, open-source licences |

Category advice always ends with the same three actions: do not reply or click, block and report the sender inside WhatsApp, and if money or details were already shared, call the national cybercrime helpline 1930 or report at cybercrime.gov.in. These open the dialer or the browser by user tap only and carry no message content.

"Share this warning" builds a text summary (reasons, no original message unless the user ticks a box), shows it, and hands it to the system share sheet.

Feedback is local: "Not a scam" stores the fingerprint in `suppressed_fingerprints`, dismisses the alert and offers "Trust this sender". "This is a scam" on a Caution marks it confirmed. A later version can offer an explicit, user-reviewed export of feedback; the MVP does not transmit anything.

All screens must work with TalkBack and at 200% font scale; alert level is never conveyed by colour alone.

---

## 14. Privacy and security enforcement

CI tasks under `tools/ci/`, all blocking:

| Check | Fails when |
|---|---|
| `verifyPermissions` | The release merged manifest contains any `uses-permission` outside the allow-list: `POST_NOTIFICATIONS`, `VIBRATE`, and the app's own `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` that `androidx.core` adds |
| `verifyDependencies` | The resolved release classpath contains a forbidden group: `com.google.firebase`, `com.google.android.gms`, `com.google.mlkit`, `com.google.android.play`, `io.sentry`, `com.facebook`, `com.appsflyer`, `com.amplitude`, `com.mixpanel`, or any HTTP client (`okhttp`, `retrofit`, `ktor-client`) |
| `verifyExportedComponents` | Any component is exported other than the launcher activity, `CheckMessageActivity` and the listener service (which is permission-protected) |
| `verifyNoContentLogging` | `:capture`, `:engine` or `:data` call `android.util.Log`, `println` or `Timber` outside the audited `SafeLog` wrapper, which accepts only enumerated event codes and numbers |
| `verifyEngineIsPure` | `:engine` resolves any `android.*` or `androidx.*` dependency |

Also required:

- Libraries merge their own manifest entries. Before adding any dependency, check what it contributes to the merged manifest. WorkManager, for example, brings `ACCESS_NETWORK_STATE`, `WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED`, which is why this design avoids it.
- Debug builds enable `StrictMode` network detection as a tripwire.
- No `WebView` anywhere.
- Canary test (section 16) proving benign text does not reach disk or logcat.
- R8 full mode for release; keep rules only where needed.

Google Play notes (verify against current policy at submission time):

- Target API 36.
- Data safety form: no data collected, no data shared, since nothing leaves the device.
- A privacy policy URL is still required for the listing; it is hosted outside the app.
- The in-app prominent disclosure in 13.1 step 3 must precede the settings redirect.
- The listing may say the app "works with WhatsApp notifications" but must not use WhatsApp's name or marks in the app title or icon.
- New personal developer accounts must run a closed test before production access; check the current tester count and duration.

Sideloading note: since Android 13, apps installed from a downloaded APK are blocked from notification access until the user finds "Allow restricted settings" in App info, and Android 15 tightened this. Installs from Play or over `adb` are unaffected. Use Play testing tracks for testers.

---

## 15. Performance budgets

| Metric | Budget | Measured on |
|---|---|---|
| Analysis time per message, p95 | 150 ms | Low-end device (2 GB RAM class), 1,000-character message |
| Engine cold initialisation (packs, automaton, model) | 400 ms | Same; done lazily on listener connect, off the main thread |
| Added memory for the engine | 40 MB | Steady state |
| Release APK size | 15 MB | Universal APK |
| Work on the main thread in the listener | Package-name check only | |
| Background work when no notifications arrive | None | No polling, wake locks, alarms or foreground service |

Add a macro-benchmark or instrumented timing test for the first two rows.

---

## 16. Testing and evaluation

### 16.1 Unit tests (JVM, `:engine`)

- Normalizer: table-driven cases for each step, including Indic text with ZWJ/ZWNJ and offset-map round trips.
- Extractors: at least 20 cases per entity type, with obfuscated URLs, IDN domains, Indic digits and UPI-versus-email confusion.
- Signals: for every signal, at least three positive and three negative cases; for every Tier 1 and Tier 2 language pack, at least two positives per lexicon intent it defines.
- Combos and fusion: the worked examples in section 10.
- Model: loader rejects a bad CRC and a featurizer-version mismatch; golden-vector test where 200 fixed texts must produce committed feature indices exactly.
- Robustness: empty, whitespace-only, 100,000-character, emoji-only and malformed-surrogate inputs never throw.

### 16.2 Corpus evaluation (`engine-cli eval`)

Prints a confusion matrix overall and by language, category and origin, and writes `eval/report.json`. CI gates on the frozen test split at Balanced sensitivity:

| Gate | Tier 1 | Tier 2 | Tier 3 |
|---|---|---|---|
| Danger precision | at least 0.97 | at least 0.95 | reported |
| Scam recall at Caution or above | at least 0.90 | at least 0.80 | reported |
| Benign rows raised to Danger | at most 0.3% | at most 0.5% | reported |
| Benign rows raised to Caution or above | at most 2% | at most 3% | reported |

The real-world set is reported separately and ungated until it exceeds 300 scam and 1,000 benign rows; after that it becomes the gate that matters.

An adversarial subset (homoglyphs, spacing, shorteners, split messages, mixed scripts) is reported on its own line.

### 16.3 Capture tests

- Parser tests run on JSON fixtures of notification extras. Until real fixtures arrive (section 19, item 1), write fixtures from the documented `MessagingStyle` structure and mark them `synthetic`.
- A debug-only "Fake WhatsApp" screen posts `MessagingStyle` notifications from the app's own package, covering one-to-one, group, number-only sender, accumulated re-posts and document messages. The listener accepts the app's own package in debug builds only.
- A debug-only "Notification recorder" writes sanitised extras of monitored notifications to app-private storage for retrieval over `adb`. It is off by default, shows a persistent warning while on, and is compiled out of release builds.
- Instrumented end-to-end test: grant access with `adb shell cmd notification allow_listener <component>`, post a fake scam notification, assert the warning notification and the alert row; post a benign one, assert neither.

### 16.4 Privacy tests

- Canary test: push 200 benign messages containing a unique canary string through the full app pipeline, then assert the string appears nowhere in the app's data directory or in captured logcat.
- Retention test: alerts older than the retention period are gone after app start.
- Wipe test: "Delete all data" leaves empty tables and no Keystore keys.

---

## 17. Milestones

**M0. Scaffold and guardrails.** Multi-module project, version catalog, CI running lint, Detekt, unit tests and all section 14 checks. `AGENTS.md` points at this document.
Done when: a trivial release build passes every check, and adding `INTERNET` to the manifest or an HTTP client to dependencies fails CI.

**M1. Engine core, rules only.** Normalizer, extractors, packs loader, Aho-Corasick matcher, signal engine, combos, fusion (model absent), explanations, `engine-cli explain` and `eval`. Packs for `en`, `hi`, `hi-Latn`; seed `brands.json` and lists.
Done when: section 16.1 tests pass, and rules-only evaluation on a seed corpus of at least 300 scam and 600 benign Tier 1 rows reaches Danger precision 0.95 and Caution-or-above recall 0.75.

**M2. Capture.** Listener service, parser, deduplication, context buffer, share and paste entry points, Fake WhatsApp and recorder debug tools.
Done when: the instrumented end-to-end test passes, re-posted notifications never double-alert, and the main-thread budget holds.

**M3. App UX and storage.** Onboarding, notifications, all screens in 13.3, Room with encryption, retention, wipe, feedback, per-app language with `en` and `hi` UI strings.
Done when: a fresh install can be onboarded, receive a fake scam, show the alert with reasons and highlights, and every section 16.4 test passes.

**M4. ML model.** Dataset tooling, templates, featurizer CLI, training and export, Kotlin inference, attribution highlights, threshold tuning.
Done when: the export round-trip matches within 0.01, Tier 1 gates in 16.2 pass, and the model demonstrably adds recall over rules-only on the dev split at equal Danger precision (report both).

**M5. Regional languages.** Tier 2 packs and UI strings, then Tier 3 packs; per-language evaluation data; brand aliases in each script.
Done when: Tier 2 gates pass, Tier 3 is reported, and every unreviewed pack is labelled beta in the app.

**M6. Hardening and release.** Performance budgets verified on a low-end device, accessibility pass, OEM battery guidance, Play listing text and disclosure review, signed release bundle, README covering build, evaluation and how to verify the privacy claims.
Done when: all budgets, gates and checks pass on the release build.

---

## 18. Known limitations and risks

- **Coverage gaps are structural.** Muted chats, messages read in an open chat, truncated long messages, media contents and cloned or work-profile WhatsApp are invisible to this design. The app must say so in onboarding instead of implying total protection.
- **Notification format can change.** WhatsApp can alter its notification structure in any update. The parser is fixture-driven and has fallbacks, but expect maintenance.
- **Offline means stale.** With no network, new scam domains and wordings reach users only through app updates. Rules that key on structure (look-alike domains, APK files, PIN-to-receive) age better than blocklists; prioritise them.
- **Data is the real bottleneck.** Synthetic templates will produce a model that looks better in evaluation than in the field. Real messages, especially real hard negatives in each language, decide quality.
- **False alarms cost trust quickly.** A warning on a genuine bank message teaches the user to ignore warnings. Favour precision at Danger and use Caution for uncertainty.
- **Platform competition.** Google and WhatsApp are both adding their own on-device scam warnings. The defensible ground is India-specific patterns, regional languages, explanation quality and verifiable privacy.
- **Notification access is a powerful permission.** The same access is what spyware asks for. The no-network build, the privacy proof screen and, ideally, open-sourcing the code are what make the request reasonable.
- **Adversaries adapt.** Once the app is public, scammers can test messages against it. Offline detection cannot hide its rules; plan on regular pack updates.

Post-MVP candidates: SMS and Telegram sources, OCR for image scams, family mode (alerts mirrored to a trusted relative, which needs a network design of its own), a distilled transformer classifier, on-device LLM explanations on capable phones, and optional privacy-preserving blocklist updates.

---

## 19. Inputs only the human can supply

1. **Real notification fixtures.** Recorder output from two or three phones (ideally Pixel, Samsung, Xiaomi or similar) covering one-to-one, group, number-only sender, business account, document and media messages, on both WhatsApp packages. Needed to confirm the "verify" items in section 5.2.
2. **Real messages for evaluation.** Scam messages across categories and languages, and many ordinary messages, collected with consent and stripped of personal details.
3. **Native-speaker review** of each language pack and its reason translations.
4. **Verified official domains** for `brands.json`, each with source and date.
5. **Accounts and keys.** Play developer account, upload signing key, a hosted privacy policy.
6. **Product decisions.** Final name and branding, whether to open-source, which Tier 2 language to prioritise if time is short.

---

## 20. Kickoff prompt for the coding agent

```
You are building the Android app specified in docs/ARCHITECTURE.md. Read it fully before
writing code.

Rules:
- Section 2 invariants override everything. If something conflicts with them, stop and ask.
- Work milestone by milestone (section 17). At the end of each, run the full test and CI
  check suite, report results against that milestone's "Done when" criteria, and wait for
  my go-ahead before starting the next.
- Use the latest stable versions of the tools in section 3 and pin them in
  gradle/libs.versions.toml.
- Items marked "verify" and everything in section 19 need input from me. Stub them with
  clearly labelled placeholders and list them in OPEN_QUESTIONS.md; never fabricate
  real-looking data, domains or fixtures.
- Keep a CHANGELOG.md of decisions you made where the spec was silent.

Start with milestone M0.
```

---

## References checked while writing (October 2026)

- Google Play target API requirements: https://developer.android.com/google/play/requirements/target-sdk
- Android 15 redaction of sensitive notifications for third-party listeners: https://www.androidauthority.com/android-15-sensitive-notifications-3416414
- Restricted settings for sideloaded apps on Android 15: https://androidauthority.com/android-15-restricted-settings-sideloading-3481098
- `NotificationCompat.MessagingStyle`: https://developer.android.com/reference/androidx/core/app/NotificationCompat.MessagingStyle
- 16 KB page-size requirement for native libraries: https://developer.android.com/guide/practices/page-sizes
- Google Play policy on the Accessibility API (why it is excluded): https://support.google.com/googleplay/android-developer/answer/10964491
