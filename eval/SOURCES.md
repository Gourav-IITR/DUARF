# Evaluation Corpus Provenance (§9.5)

## Seed Corpus (Milestone M1)
- **Path**: `eval/corpus.jsonl`
- **Total rows**: 1,000 (350 scam, 650 benign)
- **Tiers covered**: Tier 1 (`en`, `hi`, `hi-Latn`)
- **Categories covered**: All 14 architecture categories, including MALICIOUS_APK, PHISHING_BANK_KYC, AUTHORITY_DIGITAL_ARREST, UTILITY_DISCONNECT, UPI_PAYMENT_FRAUD, OTP_ACCOUNT_TAKEOVER, REMOTE_ACCESS, JOB_TASK, INVESTMENT_TRADING, LOTTERY_PRIZE, LOAN_CREDIT, DELIVERY_COURIER, IMPERSONATED_CONTACT.
- **Hard negatives included**: Genuine bank debit/credit alerts, OTP deliveries with non-sharing warnings, genuine courier updates, bill notifications, wedding invitations, casual peer-to-peer chats, scam warning forwards.
