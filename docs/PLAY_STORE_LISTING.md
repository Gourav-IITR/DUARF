# Google Play Store Listing & Compliance Guide (§14, §17 Milestone M6)

## 1. Store Metadata

- **App Name**: `DUARF: On-Device Scam Warning`
  *(Strict adherence to trademark guidelines: "WhatsApp" is NEVER used in the app title or app icon).*
- **Short Description (max 80 chars)**:
  `On-device scam warning for notifications. No internet permission.`
- **Category**: Tools / Security

---

## 2. Full Description (max 4000 chars)

```
DUARF helps protect you from financial fraud, digital arrest scams, malicious APKs, and fake payment requests by analysing incoming notifications in real time.

PRIVACY BY DESIGN — NO INTERNET ACCESS
• Zero Internet Permission: DUARF does not declare or request the android.permission.INTERNET permission in its manifest. It cannot send data anywhere.
• Local In-Memory Analysis: Messages that aren't flagged are analysed in memory and discarded. Flagged alerts are saved only on your phone, and you can delete them anytime.
• Transparent & Open: Built with an auditable open-source engine.

HOW IT WORKS
When you receive a notification from supported messaging apps (such as WhatsApp and SMS), DUARF's on-device engine inspects the text for common fraud patterns:
• Digital Arrest & Authority Impersonation: Fake CBI, Police, or customs extortion threats.
• Malicious APK Downloads: Fake traffic e-challan or utility update app links.
• UPI PIN & Cashback Frauds: Deceptive QR codes or requests asking for your UPI PIN to "receive" money.
• Account Expiry & Urgency Pretexts: False threats claiming your bank account or SIM is blocked.

When an alert is flagged, DUARF delivers a clear warning with plain-language explanations and safe next steps.

LANGUAGE SUPPORT & DETECTION COVERAGE
DUARF's scam detection engine operates at different maturity tiers depending on language:
• Full Detection Support: English, Hindi (Devanagari), and Hinglish (Latin-script Hindi).
• Beta Support: Bengali (বাংলা), Marathi (मराठी), Telugu (తెలుగు), Tamil (தமிழ்), Odia (ଓଡ଼ିଆ). Detection is functional and being validated.
• Early Preview (Detection Limited): Gujarati (ગુજરાતી), Kannada (ಕನ್ನಡ), Malayalam (മലയാളം), Punjabi (ਪੰਜਾਬੀ). Detection capabilities are currently limited in these languages.
(Note: While the app interface supports 12 regional languages, scam detection coverage adheres to the tiers above.)

PROMINENT DISCLOSURE & REQUIRED PERMISSIONS
DUARF requires Android's Notification Access permission (NotificationListenerService) to detect scam messages.
• Purpose: Only used to read incoming message text from monitored messaging apps (WhatsApp and SMS) to check for fraud indicators.
• Handling: Analysis runs strictly on your device. DUARF never accesses the internet and never shares or sells your information.
• Control: You can revoke Notification Access at any time in Android System Settings.
```

---

## 3. Play Console Policy Requirements

### 3.1 Closed Testing (12 Testers / 14 Continuous Days)
Per Google Play Policy ([support.google.com/googleplay/android-developer/answer/14151465](https://support.google.com/googleplay/android-developer/answer/14151465)):
- Personal developer accounts created after November 13, 2023 must run a closed test with at least **12 testers opted in for at least 14 days continuously** before applying for production release access.
- Tester recruitment should include users across target device types (Xiaomi, Samsung, OnePlus, Vivo/iQOO) to ensure NotificationListenerService stability and battery saver configuration.

### 3.2 Data Safety Section Declarations
Ground all answers in verified manifest and code invariants ([support.google.com/googleplay/android-developer/answer/10787469](https://support.google.com/googleplay/android-developer/answer/10787469)):
- **Data Collection**: No data collected (Select "No").
- **Data Sharing**: No data shared with third parties (Select "No").
- **Tracking**: App does not track users (Zero third-party SDKs, zero analytics).
- **Security Practices**:
  - All local on-device alert data encrypted at rest using Android Keystore AES-256-GCM (§12).
  - Users can delete all stored alerts and data directly in the app at any time via "Delete all data".
- **Optional Family Contact**:
  - One optional contact (name + number) stored encrypted on the device only (Android Keystore AES-256-GCM, platform javax.crypto only), never shared, and deleted by "Delete all data".
  - Requires zero contact permissions (`READ_CONTACTS` not requested; manual user entry) and zero calling permissions (`CALL_PHONE` not requested; opens system dialer with `ACTION_DIAL`).

### 3.3 Prominent Disclosures & Special App Access
Per Google Play Policy on Special App Access and Prominent Disclosures ([support.google.com/googleplay/android-developer/answer/9799150](https://support.google.com/googleplay/android-developer/answer/9799150)):
- Before directing users to the system settings screen for `NotificationListenerService`, the app displays a dedicated in-app onboarding step (Step 4 & 5) explaining:
  1. Exactly what data is accessed (notification text and sender from monitored apps).
  2. How it is processed (analyzed in volatile memory and discarded if benign).
  3. Where it is stored (only flagged alerts stored locally in encrypted Room database).
  4. Confirmation that no internet permission exists.
