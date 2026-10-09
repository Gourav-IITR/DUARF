#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Gourav Mahunta

"""
tools/eval/generate_new_tier3_test.py
Generates fresh frozen Tier 3 held-out test splits (eval/test_{gu,kn,ml,pa}.jsonl)
from completely disjoint template structures and fresh phrasing patterns.
Strictly adheres to:
1. Zero PII (§14, tools/ci/check_pii.py).
2. Grouped IDs with zero train/dev template leakage.
3. Completely separate pass written prior to M6 Tier 3 rule fixes.
"""

import json
import os
import random

# Fixed seed for deterministic reproduction
random.seed(20261008)

def make_rows(templates, label, lang, count_target, id_prefix, group_prefix):
    rows = []
    num_templates = len(templates)
    for i in range(count_target):
        tpl_idx = i % num_templates
        tpl_data = templates[tpl_idx]
        text_tpl, cat, sender_kind, app, sender_disp = tpl_data
        
        text = text_tpl
        if "{amt}" in text:
            amt = random.choice(["320", "650", "1,450", "2,800", "4,200", "6,500", "18,000"])
            text = text.replace("{amt}", amt)
        if "{ref}" in text:
            ref = str(random.randint(100000, 999999))
            text = text.replace("{ref}", ref)
        if "{date}" in text:
            day = random.randint(1, 28)
            text = text.replace("{date}", f"{day:02d}-11-2026")

        row = {
            "id": f"{id_prefix}-{i+1:03d}",
            "text": text,
            "label": label,
            "category": cat,
            "lang": lang,
            "sender_kind": sender_kind,
            "is_group": False,
            "origin": "synthetic",
            "group_id": f"{group_prefix}-tpl-{tpl_idx+1:02d}",
            "notes": "",
            "app": app,
            "sender_display": sender_disp
        }
        rows.append(row)
    return rows

# ==============================================================================
# 1. GUJARATI (gu) - Fresh disjoint test templates
# ==============================================================================

TEST2_SCAM_GU = [
    # 1. Utility disconnect threat
    ("વીજળી ગ્રાહક ધ્યાન આપો: પાછલા મહિનાનું વીજ બિલ અપડેટ નથી થયું. આજે રાત્રે લાઈન કાપી દેવામાં આવશે. તાત્કાલિક વીજ અધિકારીનો સંપર્ક કરો: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 2. Digital arrest authority
    ("સેન્ટ્રલ નાર્કોટિક્સ બ્યુરો: તમારા નામ પર ગેરકાયદે પાર્સલ જપ્ત કરાયું છે. તાત્કાલિક તપાસ માટે વિડીયો કોલ પર હાજર થાઓ અને કોઈને પણ જણાવશો નહીં.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 3. Malicious APK via invite
    ("જન્મદિવસની પાર્ટીનું ડિજિટલ આમંત્રણ પત્રિકા જોવા માટે birthday_invite.apk ઇન્સ્ટોલ કરો.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 4. Bank KYC Phishing
    ("બેંક એલર્ટ: પાન કાર્ડ લિંક ન હોવાથી તમારું ખાતું આજે રાત્રે સ્થગિત થશે. તરત અપડેટ કરો: http://sbi-pan-kyc.online", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 5. Traffic Challan APK
    ("ટ્રાફિક વિભાગ સૂચના: વાહન નિયમ ભંગ બદલ ઈ-ચલણ કાપવામાં આવ્યું છે. ચલણ વિગતો જુઓ challan_notice.apk ડાઉનલોડ કરીને.", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 6. UPI PIN Lure
    ("રૂ. {amt} નું ઇનામ તમારા ખાતામાં જમા થવા તૈયાર છે. રકમ પ્રાપ્ત કરવા યુપીઆઈ પીન દાખલ કરો.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 7. OTP ask
    ("તમારી પ્રોફાઇલ વેરિફિકેશન માટે આવેલા ૬ અંકના સુરક્ષા કોડ મને તરત મોકલો.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 8. Remote Access App
    ("બેંકિંગ એપ્લિકેશન અપડેટમાં તકલીફ છે? સહાય માટે TeamViewer QuickSupport એપ ડાઉનલોડ કરો.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 9. Job Task Scam
    ("ઘરે બેઠા રેટિંગ આપીને રોજના ₹૪૫૦૦ કમાઓ. આજે જ કામ શરૂ કરવા ટેલિગ્રામમાં મેસેજ કરો: http://t.me/daily_income_gu", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 10. Loan Fee Scam
    ("સરકારી સબસિડી લોન મંજૂર થઈ છે રૂ. ૭,૦૦,૦૦૦. ફાઈલ ચાર્જ ₹૨,૫૦૦ ભરવા માટે આ લિંક પર ક્લિક કરો: http://mudra-subsidy.biz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 11. Courier Delivery Phishing
    ("ડીટીડીસી કુરિયર: મકાન નંબર ખૂટતો હોવાથી ડિલિવરી અટકી છે. સરનામું સુધારવા માટે લિંક ખોલો: http://dtdc-redelivery.info", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 12. Impersonated Contact
    ("નમસ્તે, ફોન ખોવાઈ ગયો હોવાથી આ નવા નંબર પરથી વાત કરું છું. હોસ્પિટલના બિલ માટે અર્જન્ટ પૈસા જોઈએ છે.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 13. Phishing link
    ("બેંક ઓફ બરોડા: તમારી ક્રેડિટ કાર્ડ રિવોર્ડ પોઇન્ટ્સ એક્સપાયર થઈ રહ્યા છે. કેશ મેળવવા ક્લિક કરો: http://bob-points.link", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 14. Lottery Scam
    ("તમે કૌન બનેગા કરોડપતિ લકી ડ્રોમાં ₹૫૦ લાખ જીત્યા છો. ઈનામ ક્લેમ કરવા રજિસ્ટ્રેશન ફી ભરો.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 15. DGVCL Electricity Scam
    ("ડીજીવીસીએલ પાવર: બાકી બીલ નહિ ભરો તો ૨ કલાકમાં મીટર જપ્ત થશે. પેમેન્ટ લિંક: http://dgvcl-bill.click", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST2_BENIGN_GU = [
    ("Dear Consumer, your bill for consumer no 84729104 is generated. Pay online securely at https://ugvcl.com - UGVCL", "UTILITY", "DLT_HEADER", "SMS", "GU-UGVCL-G"),
    ("તમારા A/c XX8214 માંથી INR {amt}.00 {date} ના રોજ સફળતાપૂર્વક ચૂકવાયા છે. હેલ્પલાઇન: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 32,500.00 credited to A/c XX1092 on {date}. UPI Ref: {ref}. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("492019 એ તમારો લોગિન માટેનો સુરક્ષા ઓટીપી છે. આ કોડ કોઈની સાથે પણ શેર કરશો નહીં. - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("તમારું જિયો રિચાર્જ સફળતાપૂર્વક થઈ ગયું છે. અનલિમિટેડ કૉલિંગ અને ડેટા સક્રિય છે. - Jio", "TELECOM", "DLT_HEADER", "SMS", "JM-JIOINF-P"),
    ("સાયબર ક્રાઈમ હેલ્પલાઇન 1930: અજાણી લિંક પર ક્લિક કરશો નહીં અને ઓટીપી ક્યારેય આપશો નહીં. - Gujarat Police", "GOVERNMENT", "DLT_HEADER", "SMS", "GU-GUJPOL-G"),
    ("દિવાળી પર્વની આપ સૌને અને આપના પરિવારને ખૂબ ખૂબ શુભેચ્છાઓ! નૂતન વર્ષાભિનંદન.", "CHAT", "NAMED", "WHATSAPP", "Alkesh"),
    ("કાલે બપોરે જમ્યા પછી ઓફિસેથી સીધા ઘરે આવી જજે, મહેમાન આવવાના છે.", "CHAT", "NAMED", "WHATSAPP", "Bina"),
    ("રાજકોટથી વડોદરા બસની ટિકિટ બુક થઈ ગઈ છે, સમયસર પહોંચી જજે.", "CHAT", "NAMED", "WHATSAPP", "Dharmesh"),
    ("પ્રોજેક્ટ રિપોર્ટની પ્રિન્ટ કઢાવી લીધી છે, કાલે સવારે સબમિટ કરી દઈશું.", "CHAT", "NAMED", "WHATSAPP", "Hitesh"),
    ("શાકભાજી અને દૂધ લઈ લીધું છે, બીજું કઈ લાવવાનું હોય તો ફોન કરજે.", "CHAT", "NAMED", "WHATSAPP", "Pooja"),
    ("દાદાજીની તબિયત હવે ઘણી સારી છે, ડોક્ટરે ચિંતા કરવાની ના પાડી છે.", "CHAT", "NAMED", "WHATSAPP", "Mahesh"),
    ("કોલેજના વાર્ષિક મહોત્સવ માટે તૈયારીઓ શરૂ થઈ ગઈ છે, તારે ભાગ લેવાનો છે.", "CHAT", "NAMED", "WHATSAPP", "Sonal"),
    ("લગ્નની વર્ષગાંઠની ખૂબ ખૂબ શુભેચ્છાઓ! આપ બંને હંમેશા ખુશ રહો.", "CHAT", "NAMED", "WHATSAPP", "Manoj"),
    ("આ પુસ્તક વાંચીને ખૂબ આનંદ થયો, તારે પણ એકવાર જરૂર વાંચવું જોઈએ.", "CHAT", "NAMED", "WHATSAPP", "Varsha"),
    ("આજે સુરતમાં સવારથી જ સુંદર વાતાવરણ છે અને ઠંડો પવન ફૂંકાય છે.", "CHAT", "NAMED", "WHATSAPP", "Pratik"),
    ("Your Flipkart order has been shipped and is arriving soon. Track at https://flipkart.com", "DELIVERY", "DLT_HEADER", "SMS", "AD-FLIPKT-T"),
    ("આવતા શનિવારે આપણે જૂના ગીતોનો કાર્યક્રમ સાંભળવા જવાના છીએ.", "CHAT", "NAMED", "WHATSAPP", "Dipak"),
    ("સ્કૂલમાંથી બાળકોનો પ્રવાસ જવાનો છે, સંમતિ પત્રક ભરી દીધું છે.", "CHAT", "NAMED", "WHATSAPP", "Gita"),
    ("સાંજે ગરમાગરમ ભજીયા અને ચા બનાવી છે, જલ્દી ઘરે આવી જાઓ.", "CHAT", "NAMED", "WHATSAPP", "Kiran"),
    ("બેંક સુરક્ષા જાગૃતિ: યુપીઆઈ પીન માત્ર પૈસા ટ્રાન્સફર કરવા માટે જ હોય છે, સ્વીકારવા માટે નહીં. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("બેંક ઓફ બરોડા: પ્રી-એપ્રુવ્ડ પર્સનલ લોન ઓફર. પ્રોસેસિંગ ફી લાગુ. વિગતો: https://bankofbaroda.in - BOB", "BANK", "DLT_HEADER", "SMS", "VM-BOBTXN-T")
]

# ==============================================================================
# 2. KANNADA (kn) - Fresh disjoint test templates
# ==============================================================================

TEST2_SCAM_KN = [
    # 1. Utility disconnect threat
    ("ಗ್ರಾಹಕರೇ ಗಮನಿಸಿ: ನಿಮ್ಮ ಹಿಂದಿನ ತಿಂಗಳ ವಿದ್ಯುತ್ ಶುಲ್ಕ ಪಾವತಿಯಾಗದ ಕಾರಣ ಇಂದು ರಾತ್ರಿ ಲೈನ್ ಕಟ್ ಮಾಡಲಾಗುವುದು. ಸಂಪರ್ಕಿಸಿ: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 2. Digital arrest authority
    ("ದೆಹಲಿ ಸೈಬರ್ ಪೊಲೀಸ್: ನಿಮ್ಮ ಆಧಾರ್ ಸಂಖ್ಯೆಯ ಮೇಲೆ ಅಕ್ರಮ ಹಣ ವರ್ಗಾವಣೆ ಪ್ರಕರಣ ದಾಖಲಾಗಿದೆ. ತಕ್ಷಣ ವೀಡಿಯೊ ಕರೆಯಲ್ಲಿ ಹಾಜರಾಗಿ, ಕರೆ ಕಟ್ ಮಾಡಬೇಡಿ.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 3. Malicious APK
    ("ನಮ್ಮ ಮಗಳ ಮದುವೆಯ ಆಮಂತ್ರಣ ಪತ್ರಿಕೆ ನೋಡಲು wedding_invite.apk ಡೌನ್‌ಲೋಡ್ ಮಾಡಿ.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 4. Bank KYC Phishing
    ("ಕೆನರಾ ಬ್ಯಾಂಕ್: ನಿಮ್ಮ ಪ್ಯಾನ್ ಕಾರ್ಡ್ ಲಿಂಕ್ ಆಗಿಲ್ಲದಿದ್ದರೆ ಖಾತೆ ಇಂದೇ ರದ್ದಾಗುತ್ತದೆ. ತಕ್ಷಣ ನವೀಕರಿಸಿ: http://canara-kyc-verify.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 5. Traffic Challan APK
    ("ಟ್ರಾಫಿಕ್ ಪೊಲೀಸ್: ನಿಮ್ಮ ವಾಹನದ ದಂಡ ಬಾಕಿ ಇದೆ. ರಶೀದಿ ನೋಡಲು challan_report.apk ಡೌನ್‌ಲೋಡ್ ಮಾಡಿ.", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 6. UPI PIN Lure
    ("ಅಭಿನಂದನೆಗಳು! ₹{amt} ಹಣ ನಿಮ್ಮ ಖಾತೆಗೆ ಜಮಾ ಮಾಡಲು ಯುಪಿಐ ಪಿನ್ ನಮೂದಿಸಿ.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 7. OTP ask
    ("ನಿಮ್ಮ ಖಾತೆ ಪರಿಶೀಲನೆಗಾಗಿ ಬಂದಿರುವ ಓಟಿಪಿ ಸಂಖ್ಯೆಯನ್ನು ತಕ್ಷಣ ನಮಗೆ ಕಳುಹಿಸಿ.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 8. Remote Access App
    ("ಬ್ಯಾಂಕ್ ಸರ್ವರ್ ಸಮಸ್ಯೆ ಸರಿಪಡಿಸಲು AnyDesk ಅಪ್ಲಿಕೇಶನ್ ಇನ್‌ಸ್ಟಾಲ್ ಮಾಡಿ.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 9. Job Task Scam
    ("ಮನೆಯಲ್ಲೇ ಕುಳಿತು ಯೂಟ್ಯೂಬ್ ವಿಡಿಯೋ ಲೈಕ್ ಮಾಡಿ ದಿನಕ್ಕೆ ₹೩೫೦೦ ಗಳಿಸಿ. ಟೆಲಿಗ್ರಾಂ ಸಂಪರ್ಕಿಸಿ: http://t.me/kannada_tasks", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 10. Loan Fee Scam
    ("ಪ್ರಧಾನಮಂತ್ರಿ ಮುದ್ರಾ ಯೋಜನೆಯಡಿ ₹೫ ಲಕ್ಷ ಸಾಲ ಮಂಜೂರಾಗಿದೆ. ಸಂಸ್ಕರಣಾ ಶುಲ್ಕ ಪಾವತಿಸಿ: http://pm-loan-karnataka.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 11. Courier Delivery Phishing
    ("ಇಂಡಿಯಾ ಪೋಸ್ಟ್: ನಿಮ್ಮ ಪಾರ್ಸೆಲ್ ವಿಳಾಸ ತಪ್ಪಾಗಿದೆ. ತಕ್ಷಣ ಸರಿಪಡಿಸಲು ಭೇಟಿ ನೀಡಿ: http://indiapost-karnataka.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 12. Impersonated Contact
    ("ನಮಸ್ಕಾರ, ಇದು ನನ್ನ ಹೊಸ ನಂಬರ್. ಆಸ್ಪತ್ರೆಯ ತುರ್ತು ಚಿಕಿತ್ಸೆಗಾಗಿ ಸ್ವಲ್ಪ ಹಣ ಕಳುಹಿಸಿ.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 13. Bank Phishing
    ("ಎಸ್ ಬಿ ಐ ಎಚ್ಚರಿಕೆ: ನಿಮ್ಮ ಕ್ರೆಡಿಟ್ ಪಾಯಿಂಟ್ಸ್ ನಗದೀಕರಿಸಲು ತಕ್ಷಣ ಕ್ಲಿಕ್ ಮಾಡಿ: http://sbi-rewards.link", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 14. Lottery Scam
    ("ನಿಮ್ಮ ಮೊಬೈಲ್ ಸಂಖ್ಯೆಗೆ ₹೨೫ ಲಕ್ಷ ಲಾಟರಿ ಬಹುಮಾನ ಬಂದಿದೆ. ಹಣ ಪಡೆಯಲು ನೋಂದಣಿ ಶುಲ್ಕ ಕಟ್ಟಿ.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 15. MESCOM Utility Threat
    ("ಮೆಸ್ಕಾಂ ವಿದ್ಯುತ್: ಬಿಲ್ ತಕ್ಷಣ ಪಾವತಿಸದಿದ್ದರೆ ಸಂಪರ್ಕ ಕಡಿತಗೊಳಿಸಲಾಗುವುದು: http://mescom-pay.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST2_BENIGN_KN = [
    ("Dear Consumer, your BESCOM bill of Rs {amt} is generated. Pay online at https://bescom.karnataka.gov.in - BESCOM", "UTILITY", "DLT_HEADER", "SMS", "KA-BESCOM-G"),
    ("ನಿಮ್ಮ A/c XX9302 ಇಂದ INR {amt}.00 {date} ರಂದು ಪಾವತಿಯಾಗಿದೆ. ಹೆಲ್ಪ್‌ಲೈನ್: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 28,000.00 credited to A/c XX4412 on {date}. Ref: {ref}. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("382910 ಇದು ನಿಮ್ಮ ನೆಟ್‌ಬ್ಯಾಂಕಿಂಗ್ ಲಾಗಿನ್ ಓಟಿಪಿ. ಇದನ್ನು ಯಾರೊಂದಿಗೂ ಹಂಚಿಕೊಳ್ಳಬೇಡಿ. - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ನಿಮ್ಮ ಏರ್‌ಟೆಲ್ ಮೊಬೈಲ್ ರೀಚಾರ್ಜ್ ಯಶಸ್ವಿಯಾಗಿದೆ. ಧನ್ಯವಾದಗಳು. - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("ಕರ್ನಾಟಕ ಪೊಲೀಸ್ ಸೈಬರ್ ಎಚ್ಚರಿಕೆ: ಅಪರಿಚಿತ ಕರೆಗಳಿಗೆ ಓಟಿಪಿ ಅಥವಾ ಬ್ಯಾಂಕ್ ವಿವರಗಳನ್ನು ಹಂಚಿಕೊಳ್ಳಬೇಡಿ. ಹೆಲ್ಪ್‌ಲೈನ್ 1930.", "GOVERNMENT", "DLT_HEADER", "SMS", "KA-KARPOL-G"),
    ("ಯುಗಾದಿ ಹಬ್ಬದ ಹಾರ್ದಿಕ ಶುಭಾಶಯಗಳು! ಹೊಸ ವರ್ಷವು ಸುಖ ಶಾಂತಿ ತರಲಿ.", "CHAT", "NAMED", "WHATSAPP", "Manjunath"),
    ("ಇಂದು ಸಂಜೆ ಆಫೀಸ್ ಮುಗಿಸಿ ಬೇಗ ಮನೆಗೆ ಬಾ, ಅತಿಥಿಗಳು ಬರುತ್ತಿದ್ದಾರೆ.", "CHAT", "NAMED", "WHATSAPP", "Suma"),
    ("ಬೆಂಗಳೂರಿನಿಂದ ಮೈಸೂರಿಗೆ ರೈಲಿನ ಟಿಕೆಟ್ ಬುಕ್ ಆಗಿದೆ, ಸಮಯಕ್ಕೆ ತಲುಪಿ.", "CHAT", "NAMED", "WHATSAPP", "Raghavendra"),
    ("ಕಾಲೇಜಿನ ಕಾರ್ಯಕ್ರಮಕ್ಕೆ ಬೇಕಾದ ಎಲ್ಲಾ ಸಿದ್ಧತೆಗಳು ಪೂರ್ಣಗೊಂಡಿವೆ.", "CHAT", "NAMED", "WHATSAPP", "Anand"),
    ("ಮನೆಯ ಬಳಿಯ ಅಂಗಡಿಯಿಂದ ಹಾಲು ಮತ್ತು ತರಕಾರಿ ತರಲು ಮರೆಯಬೇಡಿ.", "CHAT", "NAMED", "WHATSAPP", "Geetha"),
    ("ಮಕ್ಕಳ ಪರೀಕ್ಷೆಗಳು ಮುಗಿದಿವೆ, ಮುಂದಿನ ವಾರ ಊರಿಗೆ ಹೋಗೋಣ.", "CHAT", "NAMED", "WHATSAPP", "Prasad"),
    ("ಹುಟ್ಟುಹಬ್ಬದ ಹಾರ್ದಿಕ ಶುಭಾಶಯಗಳು ಗೆಳೆಯ! ನಿಮ್ಮ ಎಲ್ಲಾ ಆಸೆಗಳು ಈಡೇರಲಿ.", "CHAT", "NAMED", "WHATSAPP", "Karthik"),
    ("ಇಂದು ಮೈಸೂರಿನಲ್ಲಿ ಮಳೆ ಬರುತ್ತಿದೆ, ಛತ್ರಿ ತೆಗೆದುಕೊಂಡು ಹೋಗಿ.", "CHAT", "NAMED", "WHATSAPP", "Sneha"),
    ("ಈ ಹೊಸ ಪುಸ್ತಕ ತುಂಬಾ ಚೆನ್ನಾಗಿದೆ, ನೀವು ಓದಲೇಬೇಕು.", "CHAT", "NAMED", "WHATSAPP", "Shwetha"),
    ("ನಾಳೆಯ ಸಭೆಗೆ ಸಂಬಂಧಿಸಿದ ಕಡತಗಳನ್ನು ಇಮೇಲ್ ಮಾಡಿದ್ದೇನೆ ನೋಡಿ.", "CHAT", "NAMED", "WHATSAPP", "Girish"),
    ("Your Amazon parcel is arriving today. Track at https://amazon.in", "DELIVERY", "DLT_HEADER", "SMS", "AD-AMAZON-T"),
    ("ಮುಂದಿನ ಭಾನುವಾರ ಎಲ್ಲರೂ ದೇವಸ್ಥಾನಕ್ಕೆ ಹೋಗೋಣ.", "CHAT", "NAMED", "WHATSAPP", "Naveen"),
    ("ಮಧ್ಯಾಹ್ನದ ಊಟಕ್ಕೆ ಬಿಸಿಬಿಸಿ ಚಿತ್ರಾನ್ನ ಮಾಡಿದ್ದೇನೆ, ಬಾ ಊಟ ಮಾಡೋಣ.", "CHAT", "NAMED", "WHATSAPP", "Radha"),
    ("ಬ್ಯಾಂಕ್ ಸುರಕ್ಷತೆ: ಹಣ ಸ್ವೀಕರಿಸಲು ಯುಪಿಐ ಪಿನ್ ಅಗತ್ಯವಿಲ್ಲ. ಪಿನ್ ಹಣ ಕಳುಹಿಸಲು ಮಾತ್ರ. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ಕೆನರಾ ಬ್ಯಾಂಕ್ ಸಾಲ ಯೋಜನೆ: ಸುಲಭ ಬಡ್ಡಿ ದರದಲ್ಲಿ ವೈಯಕ್ತಿಕ ಸಾಲ ಲಭ್ಯ. ವಿವರಗಳಿಗೆ: https://canarabank.com - Canara", "BANK", "DLT_HEADER", "SMS", "VM-CANBNK-T"),
    ("ವಾರಾಂತ್ಯದಲ್ಲಿ ಎಲ್ಲ ಹಳೆಯ ಸ್ನೇಹಿತರು ಭೇಟಿಯಾಗೋಣ.", "CHAT", "NAMED", "WHATSAPP", "Vijay")
]

# ==============================================================================
# 3. MALAYALAM (ml) - Fresh disjoint test templates
# ==============================================================================

TEST2_SCAM_ML = [
    # 1. Utility disconnect threat
    ("പ്രിയ ഉപഭോക്താവേ, വൈദ്യുതി ബിൽ കുടിശ്ശികയുള്ളതിനാൽ ഇന്ന് രാത്രി ലൈൻ വിച്ഛേദിക്കും. ഉദ്യോഗസ്ഥനെ ബന്ധപ്പെടുക: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 2. Digital arrest authority
    ("മുംബൈ ക്രൈം ബ്രാഞ്ച്: നിങ്ങളുടെ പേരിൽ മയക്കുമരുന്ന് പാർസൽ പിടികൂടി. ഉടൻ വീഡിയോ കോളിൽ ഹാജരാകുക, ആരോടും പറയരുത്.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 3. Malicious APK
    ("ഞങ്ങളുടെ പുതിയ വീടിന്റെ പാലുകാച്ചൽ ക്ഷണക്കത്ത് കാണാൻ invitation.apk ഡൗൺലോഡ് ചെയ്യുക.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 4. Bank KYC Phishing
    ("എസ്ബിഐ അറിയിപ്പ്: കെവൈസി അപ്‌ഡേറ്റ് ചെയ്യാത്തതിനാൽ അക്കൗണ്ട് ഇന്ന് റദ്ദാകും. ലിങ്ക് ക്ലിക്ക് ചെയ്യുക: http://sbi-kerala-kyc.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 5. Traffic Challan APK
    ("ട്രാഫിക് പോലീസ്: നിങ്ങളുടെ വാഹനത്തിന്റെ പിഴ അടയ്ക്കാനുണ്ട്. വിശദാംശങ്ങൾക്ക് challan_view.apk ഇൻസ്റ്റാൾ ചെയ്യുക.", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 6. UPI PIN Lure
    ("അഭിനന്ദനങ്ങൾ! ₹{amt} ക്യാഷ്ബാക്ക് ലഭിക്കാൻ നിങ്ങളുടെ യുപിഐ പിൻ അടിക്കുക.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 7. OTP ask
    ("നിങ്ങളുടെ അക്കൗണ്ട് റദ്ദാകാതിരിക്കാൻ ഫോണിൽ വന്ന ഒടിപി നമ്പർ ഉടൻ അയക്കുക.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 8. Remote Access App
    ("ബാങ്കിങ് പ്രശ്നം പരിഹരിക്കാൻ ഫോണിൽ AnyDesk ആപ്പ് ഇൻസ്റ്റാൾ ചെയ്യുക.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 9. Job Task Scam
    ("വീട്ടിലിരുന്ന് യുട്യൂബ് ലൈക്ക് ചെയ്ത് ദിവസവും ₹೪൦൦൦ സമ്പാദിക്കാം. ടെലിഗ്രാം വഴി ബന്ധപ്പെടുക: http://t.me/kerala_jobs", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 10. Loan Fee Scam
    ("പ്രധാനമന്ത്രി മുദ്ര ലോൺ ₹૫ ലക്ഷം അനുവദിച്ചു. പ്രോസസ്സിംഗ് ഫീസ് അടയ്ക്കുക: http://pm-loan-kerala.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 11. Courier Delivery Phishing
    ("ഇന്ത്യ പോസ്റ്റ്: മേൽവിലാസം തെറ്റായതിനാൽ പാഴ്സൽ ഡെലിവറി തടഞ്ഞു. വിലാസം മാറ്റാൻ: http://indiapost-kerala.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 12. Impersonated Contact
    ("ഇത് എന്റെ പുതിയ നമ്പറാണ്. അടിയന്തിര ആവശ്യത്തിന് കുറച്ചു പണം വേണം.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 13. Bank Phishing
    ("ഫെഡറൽ ബാങ്ക്: റിവാർഡ് പോയിന്റുകൾ കാലഹരണപ്പെടുന്നു. പണമാക്കാൻ ക്ലിക്ക് ചെയ്യുക: http://federal-rewards.link", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 14. Lottery Scam
    ("നിങ്ങൾക്ക് ₹೨૫ ലക്ഷം ലോട്ടറി അടിച്ചിരിക്കുന്നു. സമ്മാനം ലഭിക്കാൻ രജിസ്ട്രേഷൻ ഫീസ് നൽകുക.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 15. KSEB Utility Threat
    ("കെഎസ്ഇബി നോട്ടീസ്: കുടിശ്ശിക ഉടൻ അടച്ചില്ലെങ്കിൽ ഫ്യൂസ് ഊരും: http://kseb-quickpay.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST2_BENIGN_ML = [
    ("Dear Consumer, your KSEB electricity bill for Rs {amt} is ready. Pay safely at https://kseb.in - KSEB", "UTILITY", "DLT_HEADER", "SMS", "KL-KSEBLD-G"),
    ("നിങ്ങളുടെ A/c XX7291 ൽ നിന്ന് INR {amt}.00 {date} ൽ പിൻവലിച്ചു. ഹെൽപ്പ്‌ലൈൻ: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 38,000.00 credited to A/c XX3109 on {date}. Ref: {ref}. - Federal Bank", "BANK", "DLT_HEADER", "SMS", "AX-FEDBNK-T"),
    ("592014 ഇതാണ് നിങ്ങളുടെ ലോഗിൻ ഒടിപി. ഇത് ആരുമായും പങ്കിടരുത്. - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("നിങ്ങളുടെ ജിയോ റീചാർജ് വിജയകരമായി പൂർത്തിയായി. നന്ദി. - Jio", "TELECOM", "DLT_HEADER", "SMS", "JM-JIOINF-P"),
    ("കേരള പോലീസ് സൈബർ മുന്നറിയിപ്പ്: അപരിചിതർക്ക് ഒടിപി നൽകരുത്. പരാതികൾക്ക് 1930 ൽ വിളിക്കുക.", "GOVERNMENT", "DLT_HEADER", "SMS", "KL-KRLPOL-G"),
    ("ഓണാശംസകൾ! കുടുംബത്തോടൊപ്പം സന്തോഷത്തോടെ ആഘോഷിക്കൂ.", "CHAT", "NAMED", "WHATSAPP", "Unni"),
    ("ഇന്ന് വൈകുന്നേരം ജോലി കഴിഞ്ഞ് വരുമ്പോൾ സാധനങ്ങൾ വാങ്ങാൻ മറക്കല്ലേ.", "CHAT", "NAMED", "WHATSAPP", "Lakshmi"),
    ("എറണാകുളത്തേക്കുള്ള ട്രെയിൻ ടിക്കറ്റ് കൺഫേം ആയിട്ടുണ്ട്.", "CHAT", "NAMED", "WHATSAPP", "Rahul"),
    ("ഓഫീസ് മീറ്റിങ്ങിന്റെ റിപ്പോർട്ട് ഞാൻ മെയിൽ ചെയ്തിട്ടുണ്ട്.", "CHAT", "NAMED", "WHATSAPP", "Deepak"),
    ("കുട്ടികളെ സ്കൂളിൽ നിന്ന് കൂട്ടാൻ സമയത്ത് എത്തണേ.", "CHAT", "NAMED", "WHATSAPP", "Asha"),
    ("ഡോക്ടറെ കാണാൻ നാളെ രാവിലെ പോകാം, അപ്പോയിന്റ്മെന്റ് എടുത്തിട്ടുണ്ട്.", "CHAT", "NAMED", "WHATSAPP", "Achan"),
    ("ജന്മദിനാശംസകൾ പ്രിയ സുഹൃത്തേ! ആയുരാരോഗ്യം നേരുന്നു.", "CHAT", "NAMED", "WHATSAPP", "Sujith"),
    ("ഇന്ന് കോഴിക്കോട് നല്ല മഴയാണ്, ശ്രദ്ധിച്ചു യാത്ര ചെയ്യുക.", "CHAT", "NAMED", "WHATSAPP", "Anjali"),
    ("ഈ പുസ്തകം വളരെ നല്ലതാണ്, വായിച്ചു നോക്കൂ.", "CHAT", "NAMED", "WHATSAPP", "Reshma"),
    ("നാളെ രാവിലെ വിളിക്കാം, സംസാരിക്കാൻ കുറച്ചു കാര്യങ്ങളുണ്ട്.", "CHAT", "NAMED", "WHATSAPP", "Manoj"),
    ("Your Flipkart delivery agent is on the way. Track at https://flipkart.com", "DELIVERY", "DLT_HEADER", "SMS", "AD-FLIPKT-T"),
    ("അടുത്ത ഞായറാഴ്ച നമുക്ക് എല്ലാവർക്കും ഒത്തുചേരാം.", "CHAT", "NAMED", "WHATSAPP", "Prasad"),
    ("ഉച്ചയ്ക്ക് സദ്യ ഉണ്ടാക്കിയിട്ടുണ്ട്, കഴിക്കാൻ വാ.", "CHAT", "NAMED", "WHATSAPP", "Amma"),
    ("ബാങ്ക് സുരക്ഷാ സന്ദേശം: പണം ലഭിക്കാൻ യുപിഐ പിൻ ആവശ്യമില്ല. പിൻ പണം നൽകാൻ മാത്രം. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ഫെഡറൽ ബാങ്ക് വായ്പ: ആകർഷകമായ പലിശ നിരക്കിൽ വ്യക്തിഗത വായ്പ. വിവരങ്ങൾക്ക്: https://federalbank.co.in - Federal", "BANK", "DLT_HEADER", "SMS", "VM-FEDBNK-T"),
    ("പഴയ സുഹൃത്തുക്കളെ കാണാൻ സാധിച്ചതിൽ വളരെ സന്തോഷം.", "CHAT", "NAMED", "WHATSAPP", "Biju")
]

# ==============================================================================
# 4. PUNJABI (pa) - Fresh disjoint test templates
# ==============================================================================

TEST2_SCAM_PA = [
    # 1. Utility disconnect threat
    ("ਜ਼ਰੂਰੀ ਸੂਚਨਾ: ਬਿਜਲੀ ਦਾ ਬਿੱਲ ਬਕਾਇਆ ਹੋਣ ਕਾਰਨ ਅੱਜ ਰਾਤ ਕਨੈਕਸ਼ਨ ਕੱਟ ਦਿੱਤਾ ਜਾਵੇਗਾ। ਅਧਿਕਾਰੀ ਨੂੰ ਫੋਨ ਕਰੋ: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 2. Digital arrest authority
    ("ਦਿੱਲੀ ਪੁਲਿਸ ਸਾਈਬਰ ਸੈੱਲ: ਤੁਹਾਡੇ ਨਾਮ ਤੇ ਗੈਰਕਾਨੂੰਨੀ ਪਾਰਸਲ ਫੜਿਆ ਗਿਆ ਹੈ। ਤੁਰੰਤ ਵੀਡੀਓ ਕਾਲ ਤੇ ਪੇਸ਼ ਹੋਵੋ, ਕਿਸੇ ਨੂੰ ਨਾ ਦੱਸੋ।", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 3. Malicious APK
    ("ਸਾਡੇ ਵਿਆਹ ਦਾ ਡਿਜੀਟਲ ਕਾਰਡ ਦੇਖਣ ਲਈ wedding_card.apk ਇੰਸਟਾਲ ਕਰੋ।", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 4. Bank KYC Phishing
    ("ਐਸਬੀਆਈ ਸੂਚਨਾ: ਪੈਨ ਕਾਰਡ ਲਿੰਕ ਨਾ ਹੋਣ ਕਾਰਨ ਤੁਹਾਡਾ ਖਾਤਾ ਅੱਜ ਬੰਦ ਹੋ ਜਾਵੇਗਾ। ਲਿੰਕ ਤੇ ਅਪਡੇਟ ਕਰੋ: http://sbi-punjab-kyc.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 5. Traffic Challan APK
    ("ਟ੍ਰੈਫਿਕ ਪੁਲਿਸ: ਤੁਹਾਡੀ ਗੱਡੀ ਦਾ ਚਲਾਨ ਬਕਾਇਆ ਹੈ। ਚਲਾਨ ਦੇਖਣ ਲਈ echallan_file.apk ਡਾਊਨਲੋਡ ਕਰੋ।", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 6. UPI PIN Lure
    ("ਵਧਾਈਆਂ! ₹{amt} ਦਾ ਕੈਸ਼ਬੈਕ ਤੁਹਾਡੇ ਖਾਤੇ ਵਿੱਚ ਪਾਉਣ ਲਈ ਆਪਣਾ ਯੂਪੀਆਈ ਪਿੰਨ ਦਰਜ ਕਰੋ।", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 7. OTP ask
    ("ਤੁਹਾਡਾ ਖਾਤਾ ਚਾਲੂ ਰੱਖਣ ਲਈ ਫੋਨ ਤੇ ਆਇਆ ਓਟੀਪੀ ਕੋਡ ਤੁਰੰਤ ਮੈਨੂੰ ਭੇਜੋ।", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 8. Remote Access App
    ("ਬੈਂਕ ਐਪ ਦੀ ਖਰਾਬੀ ਠੀਕ ਕਰਨ ਲਈ AnyDesk ਐਪ ਇੰਸਟਾਲ ਕਰੋ।", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 9. Job Task Scam
    ("ਘਰ ਬੈਠੇ ਵੀਡੀਓ ਲਾਈਕ ਕਰਕੇ ਰੋਜ਼ਾਨਾ ₹੪੦੦੦ ਕਮਾਓ। ਟੈਲੀਗ੍ਰਾਮ ਤੇ ਜੁੜੋ: http://t.me/punjab_jobs", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 10. Loan Fee Scam
    ("ਪ੍ਰਧਾਨ ਮੰਤਰੀ ਮੁਦਰਾ ਯੋਜਨਾ ਤਹਿਤ ₹੫ ਲੱਖ ਦਾ ਕਰਜ਼ਾ ਮਨਜ਼ੂਰ। ਪ੍ਰੋਸੈਸਿੰਗ ਫੀਸ ਭਰੋ: http://pm-loan-punjab.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 11. Courier Delivery Phishing
    ("ਇੰਡੀਆ ਪੋਸਟ: ਪਤੇ ਦੀ ਗਲਤੀ ਕਾਰਨ ਤੁਹਾਡਾ ਪਾਰਸਲ ਰੁਕ ਗਿਆ ਹੈ। ਪਤਾ ਠੀਕ ਕਰੋ: http://indiapost-punjab.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 12. Impersonated Contact
    ("ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ, ਇਹ ਮੇਰਾ ਨਵਾਂ ਨੰਬਰ ਹੈ। ਐਮਰਜੈਂਸੀ ਕਾਰਨ ਥੋੜ੍ਹੇ ਪੈਸੇ ਭੇਜੋ।", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 13. Bank Phishing
    ("ਪੀਐਨਬੀ ਬੈਂਕ: ਤੁਹਾਡੇ ਕ੍ਰੈਡਿਟ ਪੁਆਇੰਟ ਖਤਮ ਹੋ ਰਹੇ ਹਨ। ਕੈਸ਼ ਲੈਣ ਲਈ ਕਲਿੱਕ ਕਰੋ: http://pnb-rewards.link", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    # 14. Lottery Scam
    ("ਤੁਹਾਡੀ ਲਾਟਰੀ ਵਿੱਚ ₹੨੫ ਲੱਖ ਦਾ ਇਨਾਮ ਨਿਕਲਿਆ ਹੈ। ਇਨਾਮ ਲੈਣ ਲਈ ਰਜਿਸਟ੍ਰੇਸ਼ਨ ਫੀਸ ਜਮ੍ਹਾਂ ਕਰੋ।", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    # 15. PSPCL Utility Threat
    ("ਪੀਐਸਪੀਸੀਐਲ ਨੋਟਿਸ: ਬਕਾਇਆ ਬਿੱਲ ਨਾ ਭਰਨ ਤੇ ੨ ਘੰਟੇ ਵਿੱਚ ਬਿਜਲੀ ਕੱਟੀ ਜਾਵੇਗੀ: http://pspcl-pay.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST2_BENIGN_PA = [
    ("Dear Consumer, your PSPCL electricity bill of Rs {amt} is generated. Pay online at https://pspcl.in - PSPCL", "UTILITY", "DLT_HEADER", "SMS", "PB-PSPCLD-G"),
    ("ਤੁਹਾਡੇ A/c XX5102 ਵਿੱਚੋਂ INR {amt}.00 {date} ਨੂੰ ਕੱਟੇ ਗਏ ਹਨ। ਹੈਲਪਲਾਈਨ: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 34,000.00 credited to A/c XX8921 on {date}. Ref: {ref}. - PNB Bank", "BANK", "DLT_HEADER", "SMS", "AX-PNBBNK-T"),
    ("392018 ਇਹ ਤੁਹਾਡਾ ਨੈੱਟਬੈਂਕਿੰਗ ਲੌਗਇਨ ਓਟੀਪੀ ਹੈ। ਇਸ ਨੂੰ ਕਿਸੇ ਨਾਲ ਵੀ ਸਾਂਝਾ ਨਾ ਕਰੋ। - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ਤੁਹਾਡਾ ਏਅਰਟੈੱਲ ਰੀਚਾਰਜ ਸਫਲ ਹੋ ਗਿਆ ਹੈ। ਧੰਨਵਾਦ। - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("ਪੰਜਾਬ ਪੁਲਿਸ ਸਾਈਬਰ ਸੁਰੱਖਿਆ: ਕਿਸੇ ਅਣਜਾਣ ਨਾਲ ਓਟੀਪੀ ਜਾਂ ਪਿੰਨ ਸਾਂਝਾ ਨਾ ਕਰੋ। ਹੈਲਪਲਾਈਨ 1930.", "GOVERNMENT", "DLT_HEADER", "SMS", "PB-PUNPOL-G"),
    ("ਵਿਸਾਖੀ ਦੀਆਂ ਲੱਖ-ਲੱਖ ਵਧਾਈਆਂ! ਵਾਹਿਗੁਰੂ ਤੁਹਾਡੇ ਪਰਿਵਾਰ ਤੇ ਮਿਹਰ ਰੱਖਣ।", "CHAT", "NAMED", "WHATSAPP", "Gurpreet"),
    ("ਅੱਜ ਸ਼ਾਮ ਨੂੰ ਕੰਮ ਤੋਂ ਬਾਅਦ ਜਲਦੀ ਘਰ ਆ ਜਾਣਾ, ਮਹਿਮਾਨ ਆ ਰਹੇ ਹਨ।", "CHAT", "NAMED", "WHATSAPP", "Harpreet"),
    ("ਅੰਮ੍ਰਿਤਸਰ ਤੋਂ ਦਿੱਲੀ ਦੀ ਰੇਲ ਟਿਕਟ ਬੁੱਕ ਹੋ ਗਈ ਹੈ, ਸਮੇਂ ਸਿਰ ਪਹੁੰਚ ਜਾਣਾ।", "CHAT", "NAMED", "WHATSAPP", "Simran"),
    ("ਦਫਤਰ ਦੀ ਫਾਈਲ ਮੈਂ ਤਿਆਰ ਕਰ ਦਿੱਤੀ ਹੈ, ਕੱਲ੍ਹ ਸਵੇਰੇ ਦੇਖ ਲਵਾਂਗੇ।", "CHAT", "NAMED", "WHATSAPP", "Manpreet"),
    ("ਘਰ ਲਈ ਦੁੱਧ ਅਤੇ ਸਬਜ਼ੀ ਲੈ ਆਉਣਾ, ਭੁੱਲ ਨਾ ਜਾਣਾ।", "CHAT", "NAMED", "WHATSAPP", "Jasbir"),
    ("ਬੱਚਿਆਂ ਦੇ ਸਕੂਲ ਵਿੱਚ ਛੁੱਟੀਆਂ ਹੋ ਗਈਆਂ ਹਨ, ਪਿੰਡ ਜਾਣ ਦੀ ਤਿਆਰੀ ਕਰੋ।", "CHAT", "NAMED", "WHATSAPP", "Kuldeep"),
    ("ਜਨਮਦਿਨ ਦੀਆਂ ਬਹੁਤ ਬਹੁਤ ਮੁਬਾਰਕਾਂ ਵੀਰ ਜੀ! ਖੁਸ਼ ਰਹੋ।", "CHAT", "NAMED", "WHATSAPP", "Aman"),
    ("ਅੱਜ ਲੁਧਿਆਣੇ ਵਿੱਚ ਬਹੁਤ ਠੰਢੀ ਹਵਾ ਚੱਲ ਰਹੀ ਹੈ, ਆਪਣਾ ਧਿਆਨ ਰੱਖਣਾ।", "CHAT", "NAMED", "WHATSAPP", "Navjot"),
    ("ਇਹ ਨਵੀਂ ਕਿਤਾਬ ਬਹੁਤ ਵਧੀਆ ਹੈ, ਤੁਸੀਂ ਵੀ ਜ਼ਰੂਰ ਪੜ੍ਹੋ।", "CHAT", "NAMED", "WHATSAPP", "Rajinder"),
    ("ਕੱਲ੍ਹ ਸਵੇਰੇ ਗੁਰਦੁਆਰਾ ਸਾਹਿਬ ਮੱਥਾ ਟੇਕਣ ਚੱਲਾਂਗੇ।", "CHAT", "NAMED", "WHATSAPP", "Davinder"),
    ("Your Amazon order is arriving today. Track at https://amazon.in", "DELIVERY", "DLT_HEADER", "SMS", "AD-AMAZON-T"),
    ("ਅਗਲੇ ਐਤਵਾਰ ਸਾਰੇ ਪੁਰਾਣੇ ਦੋਸਤ ਇਕੱਠੇ ਮਿਲਾਂਗੇ।", "CHAT", "NAMED", "WHATSAPP", "Jagjit"),
    ("ਦੁਪਹਿਰ ਨੂੰ ਸਰ੍ਹੋਂ ਦਾ ਸਾਗ ਅਤੇ ਮੱਕੀ ਦੀ ਰੋਟੀ ਬਣਾਈ ਹੈ, ਆ ਜਾਓ।", "CHAT", "NAMED", "WHATSAPP", "Bebe"),
    ("ਬੈਂਕ ਸੁਰੱਖਿਆ ਸੂਚਨਾ: ਪੈਸੇ ਪ੍ਰਾਪਤ ਕਰਨ ਲਈ ਯੂਪੀਆਈ ਪਿੰਨ ਦੀ ਲੋੜ ਨਹੀਂ ਹੁੰਦੀ। ਪਿੰਨ ਸਿਰਫ਼ ਭੇਜਣ ਲਈ ਹੈ। - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ਪੀਐਨਬੀ ਕਰਜ਼ਾ ਪੇਸ਼ਕਸ਼: ਆਸਾਨ ਕਿਸ਼ਤਾਂ ਤੇ ਨਿੱਜੀ ਕਰਜ਼ਾ ਉਪਲਬਧ। ਵੇਰਵੇ: https://pnbindia.in - PNB", "BANK", "DLT_HEADER", "SMS", "VM-PNBBNK-T"),
    ("ਕੱਲ੍ਹ ਸ਼ਾਮ ਨੂੰ ਫੋਨ ਕਰਾਂਗਾ, ਜ਼ਰੂਰੀ ਗੱਲ ਕਰਨੀ ਹੈ।", "CHAT", "NAMED", "WHATSAPP", "Balwinder")
]

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
    eval_dir = os.path.join(repo_root, "eval")
    os.makedirs(eval_dir, exist_ok=True)

    tier3_configs = [
        ("gu", TEST2_SCAM_GU, TEST2_BENIGN_GU),
        ("kn", TEST2_SCAM_KN, TEST2_BENIGN_KN),
        ("ml", TEST2_SCAM_ML, TEST2_BENIGN_ML),
        ("pa", TEST2_SCAM_PA, TEST2_BENIGN_PA),
    ]

    for lang, scam_tpl, ben_tpl in tier3_configs:
        scam_rows = make_rows(scam_tpl, "scam", lang, 60, f"test2-{lang}-scam", f"tst2-scam-{lang}")
        ben_rows = make_rows(ben_tpl, "benign", lang, 100, f"test2-{lang}-ben", f"tst2-ben-{lang}")
        
        # Interleave predictably with fixed seed
        all_rows = scam_rows + ben_rows
        random.shuffle(all_rows)
        
        out_path = os.path.join(eval_dir, f"test_{lang}.jsonl")
        with open(out_path, "w", encoding="utf-8") as f:
            for r in all_rows:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
        
        print(f"Generated fresh frozen {lang} test set: {out_path} ({len(all_rows)} rows: {len(scam_rows)} scam, {len(ben_rows)} benign)")

if __name__ == "__main__":
    main()
