#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Gourav Mahunta

"""
tools/eval/generate_tier3_datasets.py
Generates Tier 3 evaluation splits (dev and test) for Gujarati (gu), Kannada (kn),
Malayalam (ml), and Punjabi (pa) with regional DLT headers and zero PII violations.
"""

import json
import os
import random

# Fixed seed for deterministic generation
random.seed(42)

def make_rows(templates, label, lang, count_target, id_prefix, group_prefix):
    rows = []
    num_templates = len(templates)
    for i in range(count_target):
        tpl_idx = i % num_templates
        tpl_data = templates[tpl_idx]
        text_tpl, cat, sender_kind, app, sender_disp = tpl_data
        
        # Slight realistic variations (amounts, reference IDs, dates)
        text = text_tpl
        if "{amt}" in text:
            amt = random.choice(["450", "850", "1,200", "2,500", "3,500", "5,000", "15,000"])
            text = text.replace("{amt}", amt)
        if "{ref}" in text:
            ref = str(random.randint(100000, 999999))
            text = text.replace("{ref}", ref)
        if "{date}" in text:
            day = random.randint(1, 28)
            text = text.replace("{date}", f"{day:02d}-10-2026")

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

# ==========================================
# 1. GUJARATI (gu)
# ==========================================

DEV_SCAM_GU = [
    ("પ્રિય ગ્રાહક, બાકી બિલ ન ભરવાને કારણે આજે રાત્રે ૯:૩૦ વાગ્યે તમારું વીજળી કનેક્શન કાપી નાખવામાં આવશે. વીજળી વિભાગ અધિકારીને કૉલ કરો: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("અમદાવાદ પોલીસ ક્રાઇમ બ્રાન્ચ: તમારા આધાર નંબર પર ડ્રગ્સ પાર્સલ પકડાયું છે અને ડિજિટલ ધરપકડ વોરંટ જારી થયું છે. વિડીયો કોલ કાપશો નહીં, કોઈને કહેશો નહીં.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("અમારા લગ્નનું કાર્ડ જોવા wedding_card.pdf.apk ડાઉનલોડ કરો અને આશીર્વાદ આપો.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("એસબીઆઈ ગ્રાહક: તમારું ખાતું ૨૪ કલાકમાં બંધ થઈ જશે. કેવાયસી અપડેટ કરવા લિંક પર ક્લિક કરો: http://sbi-kyc-verify.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ટ્રાફિક પોલીસ: તમારા વાહનનું ટ્રાફિક ચલણ પેન્ડિંગ છે. ચલણ જોવા echallan.apk ડાઉનલોડ કરો. Ref: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("અભિનંદન! ₹{amt} કેશબેક મંજૂર થયું છે. પૈસા મેળવવા માટે પીન નાખો.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("બેંક મેનેજર બોલું છું, તમારું ખાતું ચાલુ રાખવા માટે ઓટીપી આપો.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("વીજળી વિભાગ: બિલ સુધારવા માટે હમણાં જ AnyDesk એપ ઇન્સ્ટોલ કરો.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ઘરે બેઠા પાર્ટ ટાઇમ કામ કરીને દરરોજ ₹૩૦૦૦ કમાઓ. યુટ્યુબ વિડીયો લાઇક કરવા ટેલિગ્રામ ગ્રુપમાં જોડાઓ: http://t.me/task123", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("પીએમ મુદ્રા યોજના હેઠળ ₹૫,૦૦,૦૦૦ લોન મંજૂર થઈ છે. પ્રોસેસિંગ ફી ભરો આ લિંક પર: http://pm-loan-charge.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ઇન્ડિયા પોસ્ટ: તમારું પાર્સલ ડિલિવરી નિષ્ફળ રહી છે. સરનામું અપડેટ કરવા લિંક પર ક્લિક કરો: http://indiapost-update.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("આ મારો નવો નંબર છે, હોસ્પિટલમાં છું તાત્કાલિક પૈસા મોકલો.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("એચડીએફસી એલર્ટ: તમારું કાર્ડ બ્લોક થઈ ગયું છે. વેરિફાય કરવા ક્લિક કરો: http://hdfc-update.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("અભિનંદન તમે ₹૨૫ લાખની લોટરી જીતી છે. ઇનામ મેળવવા માટે રજિસ્ટ્રેશન ફી જમા કરો.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("યુજીવીસીએલ વીજળી: બિલ ન ભરાય તો તરત લાઈટ કાપી નાખવામાં આવશે: http://ugvcl-pay.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

DEV_BENIGN_GU = [
    ("Dear Consumer, your UGVCL bill of Rs {amt} for Consumer No 01928374 is generated. Pay online at https://ugvcl.com - UGVCL", "UTILITY", "DLT_HEADER", "SMS", "GU-UGVCL-G"),
    ("તમારા A/c XX4321 માંથી INR {amt}.00 {date} ના રોજ કપાયા છે. હેલ્પલાઇન: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 45,000.00 credited to A/c XX9812 on {date}. Bal: INR 52,100.00. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("123456 એ તમારો નેટબેંકિંગ લોગિન માટેનો ઓટીપી છે. આ ઓટીપી કોઈની સાથે શેર ન કરો. - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("તમારું એરટેલ પ્રીપેડ રિચાર્જ સફળ થયું છે. માન્યતા ૨૮ દિવસ. આભાર. - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("ગુજરાત પોલીસ: અજાણી વ્યક્તિ સાથે ક્યારેય ઓટીપી અથવા બેંકિંગ વિગતો શેર કરશો નહીં. સાવચેત રહો.", "GOVERNMENT", "DLT_HEADER", "SMS", "GU-GUJPOL-G"),
    ("નવરાત્રીની હાર્દિક શુભકામનાઓ! માતાજી તમારા પરિવાર પર કૃપા બનાવી રાખે.", "CHAT", "NAMED", "WHATSAPP", "Parth"),
    ("આજે સાંજે સાથે જમવા જઈશું. તું સાત વાગ્યે તૈયાર રહેજે.", "CHAT", "NAMED", "WHATSAPP", "Jignesh"),
    ("અમદાવાદથી સુરત જવાની ટ્રેનની ટિકિટ બુક થઈ ગઈ છે. સીટ નંબર {ref}.", "CHAT", "NAMED", "WHATSAPP", "Bhavin"),
    ("કાલની મીટિંગ માટેની પ્રેઝન્ટેશન ફાઇલ મોકલી દીધી છે, જોઈ લેજે.", "CHAT", "NAMED", "WHATSAPP", "Chirag"),
    ("બજારમાંથી પાછા આવતી વખતે તાજા ફળો અને શાકભાજી લેતો આવજે.", "CHAT", "NAMED", "WHATSAPP", "Mummy"),
    ("ડૉક્ટરની એપોઇન્ટમેન્ટ કાલે સાંજે પાંચ વાગ્યે છે, ફાઇલ સાથે રાખજે.", "CHAT", "NAMED", "WHATSAPP", "Papa"),
    ("બોર્ડની પરીક્ષાનું પરિણામ આજે જાહેર થશે, ચિંતા ન કરતો સારું આવશે.", "CHAT", "NAMED", "WHATSAPP", "Harsh"),
    ("જન્મદિવસની ખૂબ ખૂબ શુભેચ્છાઓ મિત્ર! તારું આવનારું વર્ષ શાનદાર રહે.", "CHAT", "NAMED", "WHATSAPP", "Ketan"),
    ("નવી નવલકથા વાંચી, વાર્તા ખૂબ સરસ અને પ્રેરણાદાયી છે.", "CHAT", "NAMED", "WHATSAPP", "Neha"),
    ("આજે વડોદરામાં ભારે વરસાદ છે, બહાર નીકળો ત્યારે છત્રી સાથે રાખજો.", "CHAT", "NAMED", "WHATSAPP", "Meera"),
    ("Your Amazon delivery agent is out for delivery. Track at https://amazon.in", "DELIVERY", "DLT_HEADER", "SMS", "AD-AMAZON-T"),
    ("ગઈકાલની ક્રિકેટ મેચ ભારત બહુ સારી રીતે જીત્યું! તેં જોઈ?", "CHAT", "NAMED", "WHATSAPP", "Hardik"),
    ("આવતા રવિવારે બધા જૂના મિત્રો ભેગા મળવાના છીએ, તારે આવવાનું છે.", "CHAT", "NAMED", "WHATSAPP", "Jay"),
    ("આજે બપોરે ઢોકળા અને ખાંડવી બનાવ્યા છે, ખાવા આવજે.", "CHAT", "NAMED", "WHATSAPP", "Kaki"),
    ("બેંક સુરક્ષા સલાહ: પૈસા મેળવવા પીનની જરૂર નથી. પીન ફક્ત પૈસા મોકલવા માટે છે. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("એસબીઆઈ લોન ઑફર: તમારા માટે ₹૫,૦૦,૦૦૦ પર્સનલ લોન ઉપલબ્ધ. પ્રોસેસિંગ ફી ₹૧,૦૦૦. અરજી કરો: https://sbi.co.in - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T")
]

TEST_SCAM_GU = [
    ("તાકીદની સૂચના: વીજળી બિલ બાકી હોવાથી આજે રાત્રે વીજ પુરવઠો બંધ કરવામાં આવશે. સંપર્ક: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("દિલ્હી ક્રાઇમ બ્રાન્ચ: તમારા નામ પર શંકાસ્પદ પાર્સલ જપ્ત થયું છે. વિડીયો કોલમાં રહો, લાઇન કાપશો નહીં.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("લગ્નનું આમંત્રણ પત્રિકા જોવા invite.apk ઇન્સ્ટોલ કરો.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("એસબીઆઈ એલર્ટ: તમારું એકાઉન્ટ બ્લોક થયું છે. ફરી શરૂ કરવા ક્લિક કરો: http://sbi-unblock.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ચલણ ભરો: તમારા વાહન પર ₹૧૦૦૦ નો દંડ થયો છે. ચલણ જુઓ challan.apk ડાઉનલોડ કરીને.", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("તમને ₹{amt} નું રિવોર્ડ મળ્યું છે. રૂપિયા સ્વીકારવા યુપીઆઇ પીન નાખો.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("બેંક વેરિફિકેશન માટે તાત્કાલિક આવેલ ઓટીપી કોડ મોકલો.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("વીજળી બિલ અપડેટ કરવા માટે TeamViewer ડાઉનલોડ કરો.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("યુટ્યુબ વિડીયો લાઈક કરીને રોજ કમાઓ. ટેલિગ્રામ ચેનલમાં આવો: http://t.me/earn_now", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("સરળ લોન મંજૂર: ₹૨,૦૦,૦૦૦ મેળવવા માટે પ્રોસેસિંગ ફી જમા કરો: http://quick-loan.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("પાર્સલ અટકી ગયું છે. સાચું સરનામું આપવા લિંક ખોલો: http://speedpost-service.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("હું મુશ્કેલીમાં છું, આ મારો નવો નંબર છે, તાત્કાલિક ₹૫૦૦૦ મોકલો.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("બેંક નોટિસ: પાન કાર્ડ લિંક ન હોવાથી ખાતું સ્થગિત. અપડેટ કરવા લિંક પર જાઓ: http://pan-update.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("તમે કૌન બનેગા કરોડપતિમાં ₹૧૦ લાખ જીત્યા છો. ટેક્સ જમા કરો.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("વીજ કનેક્શન રદ થશે આજે રાત્રે. બિલ ભરો: http://bijli-bill.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST_BENIGN_GU = [
    ("UGVCL Power: Your power supply maintenance is scheduled on {date} from 10 AM to 2 PM. - UGVCL", "UTILITY", "DLT_HEADER", "SMS", "GU-UGVCL-G"),
    ("તમારા A/c XX8765 માં INR {amt}.00 જમા થયા છે {date} ના રોજ. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Transaction of INR {amt}.00 completed via HDFC NetBanking on {date}. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("456789 એ એસબીઆઈ કાર્ડ પેમેન્ટ માટેનો વેરિફિકેશન કોડ છે. - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("જિયો વેલકમ ઓફર સક્રિય છે. અમર્યાદિત 5G ડેટાનો આનંદ માણો. - Jio", "TELECOM", "DLT_HEADER", "SMS", "JX-JIOINF-P"),
    ("સાયબર સુરક્ષા સૂચના: અજાણી લિંક્સ ખોલશો નહીં. સાયબર હેલ્પલાઇન 1930. - પોલીસ", "GOVERNMENT", "DLT_HEADER", "SMS", "GU-GUJPOL-G"),
    ("દિવાળીના તહેવારની ઘણી ઘણી શુભેચ્છાઓ! નવું વર્ષ મંગલમય રહે.", "CHAT", "NAMED", "WHATSAPP", "Pooja"),
    ("કાલે સવારે આપણે સાથે મોર્નિંગ વૉક માટે જઈશું?", "CHAT", "NAMED", "WHATSAPP", "Rajesh"),
    ("બસની ટિકિટ કન્ફર્મ થઈ ગઈ છે, સમયસર પહોંચી જજે.", "CHAT", "NAMED", "WHATSAPP", "Ketan"),
    ("પ્રોજેક્ટ રિપોર્ટ તૈયાર છે, એકવાર ચેક કરી લેજે.", "CHAT", "NAMED", "WHATSAPP", "Manoj"),
    ("દૂધ અને ચા ની પત્તી લાવવાનું ભૂલતો નહીં.", "CHAT", "NAMED", "WHATSAPP", "Mummy"),
    ("દવાઓ સમયસર લેજે અને આરામ કરજે.", "CHAT", "NAMED", "WHATSAPP", "Papa"),
    ("કોલેજના એડમિશનનું ફોર્મ ભરાઈ ગયું છે.", "CHAT", "NAMED", "WHATSAPP", "Anand"),
    ("લગ્નની વર્ષગાંઠની ખૂબ ખૂબ શુભેચ્છાઓ!", "CHAT", "NAMED", "WHATSAPP", "Sunita"),
    ("પુસ્તક પૂરું કર્યું, ખૂબ પ્રેરણાદાયક વાતો છે એમાં.", "CHAT", "NAMED", "WHATSAPP", "Geeta"),
    ("આજે હવામાન ખૂબ સરસ છે, ઠંડો પવન વાઈ રહ્યો છે.", "CHAT", "NAMED", "WHATSAPP", "Kavita"),
    ("તમારો ફ્લિપકાર્ટ ઓર્ડર આજે પહોંચશે. ટ્રેક કરો: https://flipkart.com", "DELIVERY", "DLT_HEADER", "SMS", "FK-FLPKRT-T"),
    ("IPL ની ફાઇનલ મેચ જોવા માટે આપણી ટીકીટ બુક થઈ ગઈ છે!", "CHAT", "NAMED", "WHATSAPP", "Sanjay"),
    ("સ્કૂલના મિત્રોનું ગેટ-ટુગેધર આવતા મહિને રાખ્યું છે.", "CHAT", "NAMED", "WHATSAPP", "Pratik"),
    ("આજે સાંજે પાવભાજી બનાવી છે, જમવા આવજે.", "CHAT", "NAMED", "WHATSAPP", "Bhabhi"),
    ("બેંક સંદેશ: પૈસા લેવા માટે પીન નાખવો પડતો નથી. સાવચેત રહો. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("એસબીઆઈ કાર લોન: વ્યાજ દર ૮.૭૫% થી શરૂ. પ્રોસેસિંગ ફી ₹૫૦૦. તપાસો: https://sbi.co.in - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T")
]

# ==========================================
# 2. KANNADA (kn)
# ==========================================

DEV_SCAM_KN = [
    ("ಗ್ರಾಹಕರೇ, ಬಾಕಿ ಬಿಲ್ ಪಾವತಿಸದ ಕಾರಣ ಇಂದು ರಾತ್ರಿ ೯:೩೦ ಕ್ಕೆ ನಿಮ್ಮ ವಿದ್ಯುತ್ ಸಂಪರ್ಕ ಕಡಿತಗೊಳಿಸಲಾಗುವುದು. ವಿದ್ಯುತ್ ಇಲಾಖೆ ಅಧಿಕಾರಿಗೆ ಕರೆ ಮಾಡಿ: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಬೆಂಗಳೂರು ನಗರ ಪೊಲೀಸ್ ಕ್ರೈಮ್ ಬ್ರಾಂಚ್: ನಿಮ್ಮ ಆಧಾರ್ ಸಂಖ್ಯೆಯಲ್ಲಿ ಮಾದಕ ವಸ್ತು ಪಾರ್ಸಲ್ ಪತ್ತೆಯಾಗಿದೆ. ಡಿಜಿಟಲ್ ಬಂಧನ ವಾರಂಟ್ ಹೊರಡಿಸಲಾಗಿದೆ. ವೀಡಿಯೋ ಕಾಲ್ ಕಟ್ ಮಾಡಬೇಡಿ, ಯಾರಿಗೂ ಹೇಳಬೇಡಿ.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ನಮ್ಮ ಮದುವೆಯ ಆಮಂತ್ರಣ ಪತ್ರಿಕೆ ನೋಡಲು wedding_card.pdf.apk ಡೌನ್‌ಲೋಡ್ ಮಾಡಿ ಆಶೀರ್ವದಿಸಿ.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಎಸ್‍ಬಿಐ ಗ್ರಾಹಕರೇ: ನಿಮ್ಮ ಖಾತೆಯು ೨೪ ಗಂಟೆಗಳಲ್ಲಿ ಮುಚ್ಚಲ್ಪಡುತ್ತದೆ. ಕೆವೈಸಿ ಅಪ್‌ಡೇಟ್ ಮಾಡಲು ಲಿಂಕ್ ಕ್ಲಿಕ್ ಮಾಡಿ: http://sbi-kyc-verify.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ಸಂಚಾರ ಪೊಲೀಸ್: ನಿಮ್ಮ ವಾಹನದ ಟ್ರಾಫಿಕ್ ಚಲನ್ ಬಾಕಿ ಇದೆ. ಚಲನ್ ವೀಕ್ಷಿಸಲು echallan.apk ಡೌನ್‌ಲೋಡ್ ಮಾಡಿ. Ref: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ಅಭಿನಂದನೆಗಳು! ₹{amt} ಕ್ಯಾಶ್‌ಬ್ಯಾಕ್ ಅನುಮೋದಿಸಲಾಗಿದೆ. ಹಣ ಪಡೆಯಲು ಪಿನ್ ನಮೂದಿಸಿ.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಬ್ಯಾಂಕ್ ಮ್ಯಾನೇಜರ್ ಮಾತನಾಡುತ್ತಿದ್ದೇನೆ, ಖಾತೆ ಚಾಲ್ತಿಯಲ್ಲಿಡಲು ಒಟಿಪಿ ತಿಳಿಸಿ.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ವಿದ್ಯುತ್ ಇಲಾಖೆ: ತಪ್ಪಾದ ಬಿಲ್ ಸರಿಪಡಿಸಲು ತಕ್ಷಣ AnyDesk ಆ್ಯಪ್ ಇನ್‌ಸ್ಟಾಲ್ ಮಾಡಿ.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಮನೆಯಲ್ಲೇ ಕುಳಿತು ಅರೆಕಾಲಿಕ ಕೆಲಸ ಮಾಡಿ ದಿನಕ್ಕೆ ₹೩೦೦೦ ಗಳಿಸಿ. ಯೂಟ್ಯೂಬ್ ವೀಡಿಯೊ ಲೈಕ್ ಮಾಡಲು ಟೆಲಿಗ್ರಾಂ ಗ್ರೂಪ್‌ಗೆ ಸೇರಿ: http://t.me/task123", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಪಿಎಂ ಮುದ್ರಾ ಯೋಜನೆಯಡಿ ₹೫,೦೦,೦೦೦ ಸಾಲ ಮಂಜೂರಾಗಿದೆ. ಪ್ರೊಸೆಸಿಂಗ್ ಶುಲ್ಕ ನೀಡಿ ಈ ಲಿಂಕ್‌ನಲ್ಲಿ: http://pm-loan-charge.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ಇಂಡಿಯಾ ಪೋಸ್ಟ್: ನಿಮ್ಮ ಪಾರ್ಸಲ್ ವಿತರಣೆ ವಿಫಲವಾಗಿದೆ. ವಿಳಾಸ ನವೀಕರಿಸಲು ಲಿಂಕ್ ಕ್ಲಿಕ್ ಮಾಡಿ: http://indiapost-update.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ಇದು ನನ್ನ ಹೊಸ ನಂಬರ್, ಆಸ್ಪತ್ರೆಯಲ್ಲಿದ್ದೇನೆ ತುರ್ತಾಗಿ ಹಣ ಕಳುಹಿಸಿ.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಎಚ್‌ಡಿಎಫ್‌ಸಿ ಎಚ್ಚರಿಕೆ: ನಿಮ್ಮ ಕಾರ್ಡ್ ಅಮಾನತುಗೊಂಡಿದೆ. ಪರಿಶೀಲಿಸಲು ಕ್ಲಿಕ್ ಮಾಡಿ: http://hdfc-update.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ಅಭಿನಂದನೆಗಳು! ನೀವು ₹೨೫ ಲಕ್ಷ ಲಾಟರಿ ಗೆದ್ದಿದ್ದೀರಿ. ಬಹುಮಾನ ಪಡೆಯಲು ನೋಂದಣಿ ಶುಲ್ಕ ಪಾವತಿಸಿ.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಬೆಸ್ಕಾಂ ವಿದ್ಯುತ್: ಬಿಲ್ ಪಾವತಿಸದಿದ್ದರೆ ಇಂದು ರಾತ್ರಿ ಕರೆಂಟ್ ಕಟ್ ಮಾಡಲಾಗುವುದು: http://bescom-pay.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

DEV_BENIGN_KN = [
    ("Dear Consumer, your BESCOM bill of Rs {amt} for Account ID 987654321 is generated. Pay online at https://bescom.karnataka.gov.in - BESCOM", "UTILITY", "DLT_HEADER", "SMS", "KA-BESCOM-G"),
    ("ನಿಮ್ಮ ಖಾತೆ XX4321 ರಿಂದ INR {amt}.00 {date} ರಂದು ಡೆಬಿಟ್ ಆಗಿದೆ. ಸಹಾಯವಾಣಿ: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 45,000.00 credited to A/c XX9812 on {date}. Bal: INR 52,100.00. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("ನೆಟ್‌ಬ್ಯಾಂಕಿಂಗ್ ಲಾಗಿನ್‌ಗಾಗಿ ನಿಮ್ಮ ಒಟಿಪಿ 123456. ಇದನ್ನು ಯಾರೊಂದಿಗೂ ಹಂಚಿಕೊಳ್ಳಬೇಡಿ. - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ನಿಮ್ಮ ಏರ್‌ಟೆಲ್ ಪ್ರಿಪೇಯ್ಡ್ ರೀಚಾರ್ಜ್ ಯಶಸ್ವಿಯಾಗಿದೆ. ಮಾನ್ಯತೆ ೨೮ ದಿನಗಳು. ಧನ್ಯವಾದಗಳು. - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("ಕರ್ನಾಟಕ ಪೊಲೀಸ್: ಅಪರಿಚಿತರೊಂದಿಗೆ ಬ್ಯಾಂಕಿಂಗ್ ವಿವರಗಳು ಅಥವಾ ಒಟಿಪಿ ಹಂಚಿಕೊಳ್ಳಬೇಡಿ. ಜಾಗರೂಕರಾಗಿರಿ.", "GOVERNMENT", "DLT_HEADER", "SMS", "KA-KARPOL-G"),
    ("ಯುಗಾದಿ ಹಬ್ಬದ ಹಾರ್ದಿಕ ಶುಭಾಶಯಗಳು! ನಿಮ್ಮ ಬಾಳಿನಲ್ಲಿ ಸಂತೋಷ ತುಂಬಿರಲಿ.", "CHAT", "NAMED", "WHATSAPP", "Praveen"),
    ("ಇಂದು ಸಂಜೆ ಎಲ್ಲರೂ ಒಟ್ಟಿಗೆ ಊಟಕ್ಕೆ ಹೋಗೋಣ. ೭ ಗಂಟೆಗೆ ರೆಡಿಯಾಗಿರು.", "CHAT", "NAMED", "WHATSAPP", "Suresh"),
    ("ಬೆಂಗಳೂರಿನಿಂದ ಮೈಸೂರಿಗೆ ರೈಲಿನ ಟಿಕೆಟ್ ಬುಕ್ ಆಗಿದೆ. ಸೀಟ್ ಸಂಖ್ಯೆ {ref}.", "CHAT", "NAMED", "WHATSAPP", "Manjunath"),
    ("ನಾಳಿನ ಮೀಟಿಂಗ್ ಪ್ರಸ್ತುತಿ ಸಿದ್ಧಪಡಿಸಿದ್ದೇನೆ, ಒಮ್ಮೆ ಪರಿಶೀಲಿಸು.", "CHAT", "NAMED", "WHATSAPP", "Kiran"),
    ("ಮಾರುಕಟ್ಟೆಯಿಂದ ಬರುವಾಗ ತರಕಾರಿ ಮತ್ತು ಹಣ್ಣುಗಳನ್ನು ತೆಗೆದುಕೊಂಡು ಬಾ.", "CHAT", "NAMED", "WHATSAPP", "Amma"),
    ("ಡಾಕ್ಟರ್ ಅಪಾಯಿಂಟ್‌ಮೆಂಟ್ ನಾಳೆ ಸಂಜೆ ಐದು ಗಂಟೆಗೆ ಇದೆ, ಮರೆಯಬೇಡ.", "CHAT", "NAMED", "WHATSAPP", "Appa"),
    ("ಪರೀಕ್ಷೆಯ ಫಲಿತಾಂಶ ಇಂದು ಮಧ್ಯಾಹ್ನ ಬರುತ್ತದೆ, ಒಳ್ಳೆಯ ಅಂಕ ಬರುತ್ತದೆ ನಂಬು.", "CHAT", "NAMED", "WHATSAPP", "Chethan"),
    ("ಹುಟ್ಟುಹಬ್ಬದ ಹಾರ್ದಿಕ ಶುಭಾಶಯಗಳು ಗೆಳೆಯ! ನೂರು ಕಾಲ ಸುಖವಾಗಿ ಬಾಳು.", "CHAT", "NAMED", "WHATSAPP", "Darshan"),
    ("ಆ ಹೊಸ ಕನ್ನಡ ಕಾದಂಬರಿ ಓದಿದೆ, ಕಥೆ ಅದ್ಭುತವಾಗಿದೆ.", "CHAT", "NAMED", "WHATSAPP", "Divya"),
    ("ಇಂದು ಬೆಂಗಳೂರಿನಲ್ಲಿ ಭಾರಿ ಮಳೆಯಾಗುತ್ತಿದೆ, ಹೊರಗೆ ಹೋಗುವಾಗ ಕೊಡೆ ತೆಗೆದುಕೊಂಡು ಹೋಗು.", "CHAT", "NAMED", "WHATSAPP", "Roopa"),
    ("Your Amazon delivery agent is out for delivery. Track at https://amazon.in", "DELIVERY", "DLT_HEADER", "SMS", "AD-AMAZON-T"),
    ("ಆರ್‌ಸಿಬಿ ಇವತ್ತಿನ ಪಂದ್ಯವನ್ನು ಅದ್ಭುತವಾಗಿ ಗೆದ್ದಿತು! ಮ್ಯಾಚ್ ನೋಡಿದ್ಯಾ?", "CHAT", "NAMED", "WHATSAPP", "Vinay"),
    ("ಮುಂದಿನ ಭಾನುವಾರ ಶಾಲೆಯ ಸ್ನೇಹಿತರ ಭೇಟಿ ಇದೆ, ನೀನು ಖಂಡಿತ ಬರಬೇಕು.", "CHAT", "NAMED", "WHATSAPP", "Prashanth"),
    ("ಇವತ್ತು ಮಧ್ಯಾಹ್ನ ಬಿಸಿಬೇಳೆಬಾತ್ ಮಾಡಿದ್ದೇನೆ, ಊಟಕ್ಕೆ ಬಾ.", "CHAT", "NAMED", "WHATSAPP", "Akkamma"),
    ("ಬ್ಯಾಂಕ್ ಭದ್ರತಾ ಮಾಹಿತಿ: ಹಣ ಸ್ವೀಕರಿಸಲು ಪಿನ್ ನಮೂದಿಸುವ ಅಗತ್ಯವಿಲ್ಲ. ಪಿನ್ ಕೇವಲ ಹಣ ಕಳುಹಿಸಲು ಮಾತ್ರ. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ಎಸ್‍ಬಿಐ ಸಾಲದ ಕೊಡುಗೆ: ನಿಮಗೆ ₹೫,೦೦,೦೦೦ ವೈಯಕ್ತಿಕ ಸಾಲ ಲಭ್ಯವಿದೆ. ಪ್ರೊಸೆಸಿಂಗ್ ಶುಲ್ಕ ₹೧,೦೦೦. ಅರ್ಜಿ ಸಲ್ಲಿಸಿ: https://sbi.co.in - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T")
]

TEST_SCAM_KN = [
    ("ತುರ್ತು ಸೂಚನೆ: ವಿದ್ಯುತ್ ಬಿಲ್ ಬಾಕಿ ಇರುವುದರಿಂದ ಇಂದು ರಾತ್ರಿ ವಿದ್ಯುತ್ ಸರಬರಾಜು ನಿಲ್ಲಿಸಲಾಗುವುದು. ಸಂಪರ್ಕಿಸಿ: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ದೆಹಲಿ ಸೈಬರ್ ಪೊಲೀಸ್: ನಿಮ್ಮ ಖಾತೆಯಲ್ಲಿ ಅಕ್ರಮ ವಹಿವಾಟು ಪತ್ತೆಯಾಗಿದೆ. ವೀಡಿಯೋ ಕರೆಯಲ್ಲೇ ಇರಿ, ಲೈನ್ ಕಟ್ ಮಾಡಬೇಡಿ.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಮದುವೆಯ ಆಮಂತ್ರಣ ನೋಡಲು invitation.apk ಇನ್‌ಸ್ಟಾಲ್ ಮಾಡಿ.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಎಸ್‍ಬಿಐ ಎಚ್ಚರಿಕೆ: ಕೆವೈಸಿ ಅಪ್‌ಡೇಟ್ ಆಗದ ಕಾರಣ ಖಾತೆ ಸ್ಥಗಿತಗೊಂಡಿದೆ: http://sbi-unblock.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ಟ್ರಾಫಿಕ್ ದಂಡ: ನಿಮ್ಮ ವಾಹನಕ್ಕೆ ದಂಡ ವಿಧಿಸಲಾಗಿದೆ. ಚಲನ್ ನೋಡಿ challan.apk ಡೌನ್‌ಲೋಡ್ ಮಾಡಿ.", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ನಿಮಗೆ ₹{amt} ರಿವಾರ್ಡ್ ಬಂದಿದೆ. ಹಣ ಸ್ವೀಕರಿಸಲು ಯುಪಿಐ ಪಿನ್ ಹಾಕಿ.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಬ್ಯಾಂಕ್ ಪರಿಶೀಲನೆಗಾಗಿ ಬಂದಿರುವ ಒಟಿಪಿ ಕೋಡ್ ಕಳುಹಿಸಿ.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ವಿದ್ಯುತ್ ಬಿಲ್ ಪರಿಶೀಲಿಸಲು TeamViewer ಇನ್‌ಸ್ಟಾಲ್ ಮಾಡಿ.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಯೂಟ್ಯೂಬ್ ಲೈಕ್ ಮಾಡಿ ಹಣ ಗಳಿಸಿ. ಟೆಲಿಗ್ರಾಂ ಚಾನೆಲ್‌ಗೆ ಬನ್ನಿ: http://t.me/earn_now", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ತ್ವರಿತ ಸಾಲ ಮಂಜೂರಾತಿ: ₹೨,೦೦,೦೦೦ ಸಾಲಕ್ಕೆ ಪ್ರೊಸೆಸಿಂಗ್ ಶುಲ್ಕ ಪಾವತಿಸಿ: http://quick-loan.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ಪಾರ್ಸಲ್ ತಡೆಹಿಡಿಯಲಾಗಿದೆ. ವಿಳಾಸ ಸರಿಪಡಿಸಲು ಲಿಂಕ್ ತೆರೆಯಿರಿ: http://speedpost-service.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ತುರ್ತು ಪರಿಸ್ಥಿತಿಯಲ್ಲಿದ್ದೇನೆ, ಇದು ನನ್ನ ಹೊಸ ನಂಬರ್, ತಕ್ಷಣ ₹೫೦೦೦ ಕಳುಹಿಸಿ.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಪ್ಯಾನ್ ಲಿಂಕ್ ಆಗಿಲ್ಲ, ನಿಮ್ಮ ಖಾತೆ ಮುಚ್ಚಲ್ಪಡುತ್ತದೆ. ಅಪ್‌ಡೇಟ್ ಮಾಡಲು ಕ್ಲಿಕ್ ಮಾಡಿ: http://pan-update.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ನೀವು ₹೧೦ ಲಕ್ಷ ಲಕ್ಕಿ ಡ್ರಾ ಗೆದ್ದಿದ್ದೀರಿ. ತೆರಿಗೆ ಶುಲ್ಕ ಪಾವತಿಸಿ.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ಕರೆಂಟ್ ಸಂಪರ್ಕ ಕಡಿತ ತಪ್ಪಿಸಲು ತಕ್ಷಣ ಬಿಲ್ ಪಾವತಿಸಿ: http://bijli-bill.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST_BENIGN_KN = [
    ("BESCOM Power: Scheduled maintenance in your area on {date} from 10 AM to 1 PM. - BESCOM", "UTILITY", "DLT_HEADER", "SMS", "KA-BESCOM-G"),
    ("ನಿಮ್ಮ ಖಾತೆ XX8765 ಗೆ INR {amt}.00 {date} ರಂದು ಜಮೆಯಾಗಿದೆ. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Transaction of INR {amt}.00 completed via HDFC NetBanking on {date}. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("456789 ಎಸ್‌ಬಿಐ ಕಾರ್ಡ್ ಪಾವತಿಗಾಗಿ ದೃಢೀಕರಣ ಕೋಡ್ ಆಗಿದೆ. - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ಜಿಯೋ ವೆಲ್‌ಕಮ್ ಆಫರ್ ಸಕ್ರಿಯವಾಗಿದೆ. ಅನಿಯಮಿತ 5G ಡೇಟಾ ಆನಂದಿಸಿ. - Jio", "TELECOM", "DLT_HEADER", "SMS", "JX-JIOINF-P"),
    ("ಸೈಬರ್ ಸುರಕ್ಷತೆ: ಅನುಮಾನಾಸ್ಪದ ಲಿಂಕ್‌ಗಳನ್ನು ಕ್ಲಿಕ್ ಮಾಡಬೇಡಿ. ಸಹಾಯವಾಣಿ 1930. - ಪೊಲೀಸ್", "GOVERNMENT", "DLT_HEADER", "SMS", "KA-KARPOL-G"),
    ("ದೀಪಾವಳಿ ಹಬ್ಬದ ಶುಭಾಶಯಗಳು! ನಿಮ್ಮ ಮನೆಯಲ್ಲಿ ಸದಾ ಬೆಳಕು ತುಂಬಿರಲಿ.", "CHAT", "NAMED", "WHATSAPP", "Sneha"),
    ("ನಾಳೆ ಮುಂಜಾನೆ ವಾಕಿಂಗ್ ಹೋಗೋಣವೇ?", "CHAT", "NAMED", "WHATSAPP", "Ramesh"),
    ("ಬಸ್ ಟಿಕೆಟ್ ಕನ್ಫರ್ಮ್ ಆಗಿದೆ, ಸಮಯಕ್ಕೆ ಸರಿಯಾಗಿ ಬಾ.", "CHAT", "NAMED", "WHATSAPP", "Ganesh"),
    ("ಪ್ರಾಜೆಕ್ಟ್ ವರದಿ ಸಿದ್ಧವಾಗಿದೆ, ಒಮ್ಮೆ ನೋಡು.", "CHAT", "NAMED", "WHATSAPP", "Shashi"),
    ("ಮನೆಗೆ ಬರುವಾಗ ಹಾಲು ಮತ್ತು ಕಾಫಿ ಪುಡಿ ತನ್ನಿ.", "CHAT", "NAMED", "WHATSAPP", "Amma"),
    ("ಔಷಧಿಗಳನ್ನು ಸರಿಯಾದ ಸಮಯಕ್ಕೆ ತಗೋ.", "CHAT", "NAMED", "WHATSAPP", "Appa"),
    ("ಕಾಲೇಜು ಪ್ರವೇಶದ ಫಾರ್ಮ್ ಸಲ್ಲಿಕೆಯಾಗಿದೆ.", "CHAT", "NAMED", "WHATSAPP", "Anil"),
    ("ವಿವಾಹ ವಾರ್ಷಿಕೋತ್ಸವದ ಶುಭಾಶಯಗಳು!", "CHAT", "NAMED", "WHATSAPP", "Mamatha"),
    ("ಪುಸ್ತಕ ಓದಿ ಮುಗಿಸಿದೆ, ತುಂಬಾ ಸ್ಪೂರ್ತಿದಾಯಕವಾಗಿದೆ.", "CHAT", "NAMED", "WHATSAPP", "Deepa"),
    ("ಇವತ್ತು ಹವಾಮಾನ ತಂಪಾಗಿದೆ, ಮಳೆಯಾಗುವ ಸಾಧ್ಯತೆಯಿದೆ.", "CHAT", "NAMED", "WHATSAPP", "Kavya"),
    ("ನಿಮ್ಮ ಫ್ಲಿಪ್‌ಕಾರ್ಟ್ ಆರ್ಡರ್ ಇಂದು ವಿತರಿಸಲಾಗುತ್ತದೆ. ಟ್ರ್ಯಾಕ್ ಮಾಡಿ: https://flipkart.com", "DELIVERY", "DLT_HEADER", "SMS", "FK-FLPKRT-T"),
    ("ಕ್ರಿಕೆಟ್ ಪಂದ್ಯ ರೋಚಕವಾಗಿತ್ತು, ಭಾರತ ಚೆನ್ನಾಗಿ ಆಡಿತು.", "CHAT", "NAMED", "WHATSAPP", "Santosh"),
    ("ಶಾಲಾ ದಿನಗಳ ಸ್ನೇಹಿತರ ಸಭೆ ಮುಂದಿನ ತಿಂಗಳಿದೆ.", "CHAT", "NAMED", "WHATSAPP", "Raghav"),
    ("ಇಂದು ಸಂಜೆ ಮೈಸೂರು ಪಾಕ್ ಮಾಡಿದ್ದೇನೆ, ತಿನ್ನಲು ಬಾ.", "CHAT", "NAMED", "WHATSAPP", "Atthe"),
    ("ಬ್ಯಾಂಕ್ ಮಾಹಿತಿ: ಹಣ ಪಡೆಯಲು ಪಿನ್ ಅಗತ್ಯವಿಲ್ಲ. ಪಿನ್ ಕೇವಲ ಹಣ ಕಳುಹಿಸಲು ಮಾತ್ರ. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ಎಸ್‍ಬಿಐ ಗೃಹ ಸಾಲ: ಆಕರ್ಷಕ ಬಡ್ಡಿದರ. ಪ್ರೊಸೆಸಿಂಗ್ ಶುಲ್ಕ ₹೨,೦೦೦. ವಿವರಗಳಿಗೆ: https://sbi.co.in - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T")
]

# ==========================================
# 3. MALAYALAM (ml)
# ==========================================

DEV_SCAM_ML = [
    ("പ്രിയ ഉപഭോക്താവേ, കുടിശ്ശികയുള്ള ബിൽ അടയ്ക്കാത്തതിനാൽ ഇന്ന് രാത്രി 9:30 ന് നിങ്ങളുടെ വൈദ്യുതി ബന്ധം വിച്ഛേദിക്കും. വൈദ്യുതി ഉദ്യോഗസ്ഥനെ വിളിക്കുക: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("കൊച്ചി സിറ്റി പോലീസ് ക്രൈംബ്രാഞ്ച്: നിങ്ങളുടെ ആധാർ നമ്പറിൽ മയക്കുമരുന്ന് പാഴ്സൽ പിടികൂടി. ഡിജിറ്റൽ അറസ്റ്റ് വാറണ്ട് പുറപ്പെടുവിച്ചു. വീഡിയോ കോൾ വിച്ഛേദിക്കരുത്, ആരോടും പറയരുത്.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ഞങ്ങളുടെ കല്യാണക്കുറി കാണാൻ wedding_card.pdf.apk ഡൗൺലോഡ് ചെയ്ത് അനുഗ്രഹിക്കുക.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("എസ്ബിഐ ഉപഭോക്താവേ: നിങ്ങളുടെ അക്കൗണ്ട് 24 മണിക്കൂറിനുള്ളിൽ ബ്ലോക്ക് ചെയ്യപ്പെടും. കെവൈസി അപ്‌ഡേറ്റ് ചെയ്യാൻ ലിങ്കിൽ ക്ലിക്ക് ചെയ്യുക: http://sbi-kyc-verify.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("മോട്ടോർ വാഹന വകുപ്പ്: നിങ്ങളുടെ വാഹനത്തിന്റെ ട്രാഫിക് ചെല്ലാൻ അടയ്ക്കാനുണ്ട്. ചെല്ലാൻ കാണാൻ echallan.apk ഡൗൺലോഡ് ചെയ്യുക. Ref: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("അഭിനന്ദനങ്ങൾ! ₹{amt} ക്യാഷ്ബാക്ക് അനുവദിച്ചു. പണം ലഭിക്കാൻ പിൻ നൽകുക.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ബാങ്ക് മാനേജരാണ് സംസാരിക്കുന്നത്, അക്കൗണ്ട് തുടരാൻ ഒടിപി പറയൂ.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("വൈദ്യുതി വകുപ്പ്: തെറ്റായ ബിൽ തിരുത്താൻ ഉടൻ AnyDesk ആപ്പ് ഇൻസ്റ്റാൾ ചെയ്യുക.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("വീട്ടിലിരുന്ന് പാർട്ട് ടൈം ജോലി ചെയ്ത് ദിവസേന ₹3000 സമ്പാദിക്കുക. യൂട്യൂബ് ലൈക്ക് ചെയ്യാൻ ടെലിഗ്രാം ഗ്രൂപ്പിൽ ചേരുക: http://t.me/task123", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("പിഎം മുദ്ര വായ്പ: ₹5,00,000 അനുവദിച്ചു. പ്രോസസ്സിംഗ് ഫീസ് നൽകുക ഈ ലിങ്കിൽ: http://pm-loan-charge.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ഇന്ത്യ പോസ്റ്റ്: നിങ്ങളുടെ പാഴ്സൽ ഡെലിവറി പരാജയപ്പെട്ടു. വിലാസം തിരുത്താൻ ലിങ്കിൽ ക്ലിക്ക് ചെയ്യുക: http://indiapost-update.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ഇത് എന്റെ പുതിയ നമ്പറാണ്, ആശുപത്രിയിലാണ് അടിയന്തരമായി പണം അയക്കൂ.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("എച്ച്ഡിഎഫ്സി മുന്നറിയിപ്പ്: നിങ്ങളുടെ കാർഡ് ബ്ലോക്ക് ചെയ്തു. പരിശോധിക്കാൻ ക്ലിക്ക് ചെയ്യുക: http://hdfc-update.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("അഭിനന്ദനങ്ങൾ! നിങ്ങൾക്ക് ₹25 ലക്ഷം ലോട്ടറി അടിച്ചു. സമ്മാനം ലഭിക്കാൻ രജിസ്ട്രേഷൻ ഫീസ് നൽകുക.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("കെഎസ്ഇബി വൈദ്യുതി: ബിൽ അടച്ചില്ലെങ്കിൽ ഇന്ന് രാത്രി വൈദ്യുതി റദ്ദാക്കും: http://kseb-pay.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

DEV_BENIGN_ML = [
    ("Dear Consumer, your KSEB bill of Rs {amt} for Consumer No 11442233 is generated. Pay online at https://kseb.in - KSEBL", "UTILITY", "DLT_HEADER", "SMS", "KL-KSEBL-G"),
    ("നിങ്ങളുടെ A/c XX4321 ൽ നിന്ന് INR {amt}.00 {date} ൽ ഡെബിറ്റ് ചെയ്തു. ഹെൽപ്പ്‌ലൈൻ: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 45,000.00 credited to A/c XX9812 on {date}. Bal: INR 52,100.00. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("നെറ്റ്ബാങ്കിംഗ് ലോഗിൻ ചെയ്യാനുള്ള നിങ്ങളുടെ ഒടിപി 123456 ആണ്. ഇത് മറ്റാരുമായും പങ്കിടരുത്. - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("നിങ്ങളുടെ എയർടെൽ പ്രീപെയ്ഡ് റീച്ചാർജ് വിജയകരമായി പൂർത്തിയായി. കാലാവധി 28 ദിവസം. - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("കേരള പോലീസ്: അപരിചിതർക്ക് ഒടിപിയോ ബാങ്ക് വിവരങ്ങളോ കൈമാറരുത്. ജാഗ്രത പാലിക്കുക.", "GOVERNMENT", "DLT_HEADER", "SMS", "KL-KRLPOL-G"),
    ("ഹൃദയം നിറഞ്ഞ ഓണാശംസകൾ! ഐശ്വര്യവും സന്തോഷവും നിറഞ്ഞ വർഷം ആശംസിക്കുന്നു.", "CHAT", "NAMED", "WHATSAPP", "Rahul"),
    ("ഇന്ന് വൈകുന്നേരം നമുക്ക് ഒരുമിച്ച് പുറത്തുപോയി ഭക്ഷണം കഴിക്കാം. 7 മണിക്ക് ഇറങ്ങാം.", "CHAT", "NAMED", "WHATSAPP", "Anoop"),
    ("കൊച്ചിയിൽ നിന്ന് കോഴിക്കോട്ടേക്കുള്ള ട്രെയിൻ ടിക്കറ്റ് ബുക്ക് ചെയ്തു. സീറ്റ് നമ്പർ {ref}.", "CHAT", "NAMED", "WHATSAPP", "Midhun"),
    ("നാളത്തെ മീറ്റിംഗിനായുള്ള പ്രസന്റേഷൻ തയ്യാറാക്കി വെച്ചിട്ടുണ്ട്, ഒന്നു നോക്കിക്കോളൂ.", "CHAT", "NAMED", "WHATSAPP", "Vishnu"),
    ("മാർക്കറ്റിൽ നിന്ന് വരുമ്പോൾ പച്ചക്കറിയും പഴങ്ങളും വാങ്ങാൻ മറക്കല്ലേ.", "CHAT", "NAMED", "WHATSAPP", "Amma"),
    ("ഡോക്ടറുടെ അപ്പോയിന്റ്മെന്റ് നാളെ വൈകുന്നേരം 5 മണിക്കാണ്, കുറിപ്പടി കയ്യിൽ കരുതുക.", "CHAT", "NAMED", "WHATSAPP", "Achan"),
    ("പരീക്ഷാ ഫലം ഇന്ന് ഉച്ചയ്ക്ക് വരും, നല്ല മാർക്ക് ഉണ്ടാകും വിഷമിക്കേണ്ട.", "CHAT", "NAMED", "WHATSAPP", "Arjun"),
    ("ജന്മദിനാശംസകൾ സുഹൃത്തേ! ആയുരാരോഗ്യ സൗഖ്യങ്ങൾ നേരുന്നു.", "CHAT", "NAMED", "WHATSAPP", "Sujith"),
    ("ആ പുതിയ മലയാളം നോവൽ വായിച്ചു തീർത്തു, കഥ വളരെ മികച്ചതാണ്.", "CHAT", "NAMED", "WHATSAPP", "Anjali"),
    ("ഇന്ന് തിരുവനന്തപുരത്ത് നല്ല മഴയാണ്, പുറത്തിറങ്ങുമ്പോൾ കുട എടുക്കാൻ മറക്കരുത്.", "CHAT", "NAMED", "WHATSAPP", "Reshma"),
    ("Your Amazon delivery agent is out for delivery. Track at https://amazon.in", "DELIVERY", "DLT_HEADER", "SMS", "AD-AMAZON-T"),
    ("ഇന്നത്തെ കേരള ബ്ലാസ്റ്റേഴ്സ് മത്സരം സൂപ്പറായിരുന്നു! കളി കണ്ടോ?", "CHAT", "NAMED", "WHATSAPP", "Pranav"),
    ("അടുത്ത ഞായറാഴ്ച സ്കൂൾ സുഹൃത്തുക്കളുടെ ഒത്തുചേരൽ ഉണ്ട്, തീർച്ചയായും വരണം.", "CHAT", "NAMED", "WHATSAPP", "Deepak"),
    ("ഇന്ന് ഉച്ചയ്ക്ക് നല്ല നാടൻ സദ്യയും പായസവും ഉണ്ടാക്കിയിട്ടുണ്ട്, കഴിക്കാൻ വാ.", "CHAT", "NAMED", "WHATSAPP", "Ammini"),
    ("ബാങ്ക് സുരക്ഷാ സന്ദേശം: പണം ലഭിക്കാൻ പിൻ ആവശ്യമില്ല. പിൻ പണം അയക്കാൻ മാത്രമാണ്. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("എസ്ബിഐ ലോൺ ഓഫർ: നിങ്ങൾക്ക് ₹5,00,000 പേഴ്സണൽ ലോൺ ലഭ്യമാണ്. പ്രോസസ്സിംഗ് ഫീസ് ₹1,000. അപേക്ഷിക്കുക: https://sbi.co.in - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T")
]

TEST_SCAM_ML = [
    ("അടിയന്തര അറിയിപ്പ്: വൈദ്യുതി കുടിശ്ശികയുള്ളതിനാൽ ഇന്ന് രാത്രി വൈദ്യുതി വിതരണം നിർത്തും. ബന്ധപ്പെടുക: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("സൈബർ പോലീസ്: നിങ്ങളുടെ അക്കൗണ്ടിൽ നിയമവിരുദ്ധ പണമിടപാട് കണ്ടെത്തി. വീഡിയോ കോളിൽ തുടരുക, കോൾ കട്ട് ചെയ്യരുത്.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("വിവാഹ ക്ഷണക്കത്ത് കാണാൻ wedding.apk ഇൻസ്റ്റാൾ ചെയ്യുക.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("എസ്ബിഐ നോട്ടീസ്: അക്കൗണ്ട് താൽക്കാലികമായി റദ്ദാക്കി. വീണ്ടെടുക്കാൻ ക്ലിക്ക് ചെയ്യുക: http://sbi-unblock.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ട്രാഫിക് ഫൈൻ: വാഹനത്തിന് പിഴ ചുമത്തിയിരിക്കുന്നു. ചെല്ലാൻ കാണാൻ challan.apk ഡൗൺലോഡ് ചെയ്യുക.", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("നിങ്ങൾക്ക് ₹{amt} റിവാർഡ് ലഭിച്ചു. പണം സ്വീകരിക്കാൻ യുപിഐ പിൻ അടിക്കുക.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ബാങ്ക് പരിശോധനക്കായി വന്ന ഒടിപി കോഡ് നൽകുക.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("വൈദ്യുതി ബിൽ തിരുത്താൻ TeamViewer ഡൗൺലോഡ് ചെയ്യുക.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("യൂട്യൂബ് വീഡിയോ കണ്ട് പണം സമ്പാദിക്കുക. ടെലിഗ്രാം ചാനലിലേക്ക് വരൂ: http://t.me/earn_now", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("തൽക്ഷണ വായ്പ: ₹2,00,000 ലഭിക്കാൻ ഫയൽ ചാർജ് അടയ്ക്കുക: http://quick-loan.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("പാഴ്സൽ ഡെലിവറി തടസ്സപ്പെട്ടു. വിലാസം നൽകാൻ ലിങ്ക് തുറക്കുക: http://speedpost-service.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ഞാൻ അത്യാഹിതത്തിലാണ്, ഇത് പുതിയ നമ്പറാണ്, ഉടൻ ₹5000 അയക്കൂ.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("പാൻ കാർഡ് ലിങ്ക് ചെയ്തിട്ടില്ല, അക്കൗണ്ട് സസ്പെൻഡ് ചെയ്തു. ലിങ്ക്: http://pan-update.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("നിങ്ങൾക്ക് ₹10 ലക്ഷം ലക്കി ഡ്രോ സമ്മാനം അടിച്ചു. ടാക്സ് ഫീസ് നൽകുക.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("വൈദ്യുതി കണക്ഷൻ റദ്ദാക്കാതിരിക്കാൻ ബിൽ അടയ്ക്കുക: http://bijli-bill.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST_BENIGN_ML = [
    ("KSEB Power: Scheduled power shutdown in your area on {date} from 9 AM to 5 PM. - KSEBL", "UTILITY", "DLT_HEADER", "SMS", "KL-KSEBL-G"),
    ("നിങ്ങളുടെ A/c XX8765 ൽ INR {amt}.00 {date} ൽ ക്രെഡിറ്റ് ചെയ്തു. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Transaction of INR {amt}.00 completed via HDFC NetBanking on {date}. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("456789 എസ്ബിഐ കാർഡ് പേയ്‌മെന്റിനുള്ള സ്ഥിരീകരണ കോഡാണ്. - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ജിയോ വെൽക്കം ഓഫർ സജീവമാണ്. പരിധിയില്ലാത്ത 5G ആസ്വദിക്കൂ. - Jio", "TELECOM", "DLT_HEADER", "SMS", "JX-JIOINF-P"),
    ("സൈബർ സുരക്ഷ: അജ്ഞാത ലിങ്കുകളിൽ ക്ലിക്ക് ചെയ്യരുത്. ഹെൽപ്പ്‌ലൈൻ 1930. - പോലീസ്", "GOVERNMENT", "DLT_HEADER", "SMS", "KL-KRLPOL-G"),
    ("വിഷു ആശംസകൾ! നന്മയും സമൃദ്ധിയും നിറഞ്ഞ നല്ലൊരു നാളെ ആശംസിക്കുന്നു.", "CHAT", "NAMED", "WHATSAPP", "Athira"),
    ("നാളെ രാവിലെ നടക്കാൻ പോകാൻ റെഡിയാണോ?", "CHAT", "NAMED", "WHATSAPP", "Santhosh"),
    ("കെഎസ്ആർടിസി ബസ് ടിക്കറ്റ് ബുക്കിംഗ് പൂർത്തിയായി.", "CHAT", "NAMED", "WHATSAPP", "Vineeth"),
    ("ഓഫീസ് പ്രോജക്ട് റിപ്പോർട്ട് പൂർത്തിയായി, ഒന്നു പരിശോധിക്കൂ.", "CHAT", "NAMED", "WHATSAPP", "Harikumar"),
    ("വീട്ടിലേക്ക് വരുമ്പോൾ പാലും പലഹാരങ്ങളും വാങ്ങുക.", "CHAT", "NAMED", "WHATSAPP", "Amma"),
    ("ഗുളികകൾ കൃത്യസമയത്ത് കഴിക്കാൻ ഓർമ്മിക്കുമല്ലോ.", "CHAT", "NAMED", "WHATSAPP", "Achan"),
    ("കോളേജ് പ്രവേശനത്തിനുള്ള ഫീസ് അടച്ചു.", "CHAT", "NAMED", "WHATSAPP", "Akhil"),
    ("വിവാഹ വാർഷിക ആശംസകൾ!", "CHAT", "NAMED", "WHATSAPP", "Lekha"),
    ("ആ പുസ്തകം വായിച്ചു, വളരെ ചിന്തോദ്ദീപകമാണ്.", "CHAT", "NAMED", "WHATSAPP", "Maya"),
    ("ഇന്ന് നല്ല തണുത്ത കാറ്റുണ്ട്, മഴ പെയ്യാൻ സാധ്യതയുണ്ട്.", "CHAT", "NAMED", "WHATSAPP", "Sruthy"),
    ("നിങ്ങളുടെ ഫ്ലിപ്കാർട്ട് ഓർഡർ ഇന്ന് എത്തും. ട്രാക്ക് ചെയ്യുക: https://flipkart.com", "DELIVERY", "DLT_HEADER", "SMS", "FK-FLPKRT-T"),
    ("സന്തോഷ് ട്രോഫി മത്സരം ആവേശകരമായിരുന്നു.", "CHAT", "NAMED", "WHATSAPP", "Baiju"),
    ("പഴയ കൂട്ടുകാരുടെ കൂട്ടായ്മ അടുത്ത മാസമുണ്ട്.", "CHAT", "NAMED", "WHATSAPP", "Vivek"),
    ("ഇന്ന് നല്ല നെയ്ച്ചോറും ചിക്കൻ കറിയും ഉണ്ടാക്കിയിട്ടുണ്ട്, വേഗം വാ.", "CHAT", "NAMED", "WHATSAPP", "Itha"),
    ("ബാങ്ക് മുന്നറിയിപ്പ്: പണം വാങ്ങാൻ പിൻ അടിക്കേണ്ടതില്ല. ജാഗ്രത പാലിക്കുക. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("എസ്ബിഐ ഭവന വായ്പ: കുറഞ്ഞ പലിശ നിരക്കിൽ. പ്രോസസ്സിംഗ് ഫീസ് ₹2,000. വിവരങ്ങൾക്ക്: https://sbi.co.in - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T")
]

# ==========================================
# 4. PUNJABI (pa)
# ==========================================

DEV_SCAM_PA = [
    ("ਪਿਆਰੇ ਖਪਤਕਾਰ, ਬਕਾਇਆ ਬਿੱਲ ਨਾ ਭਰਨ ਕਰਕੇ ਅੱਜ ਰਾਤ ੯:੩੦ ਵਜੇ ਤੁਹਾਡਾ ਬਿਜਲੀ ਕੁਨੈਕਸ਼ਨ ਕੱਟ ਦਿੱਤਾ ਜਾਵੇਗਾ। ਬਿਜਲੀ ਬੋਰਡ ਅਧਿਕਾਰੀ ਨੂੰ ਕਾਲ ਕਰੋ: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਚੰਡੀਗੜ੍ਹ ਪੁਲਿਸ ਕ੍ਰਾਈਮ ਬ੍ਰਾਂਚ: ਤੁਹਾਡੇ ਆਧਾਰ ਨੰਬਰ ਤੇ ਨਸ਼ੀਲਾ ਪਾਰਸਲ ਫੜਿਆ ਗਿਆ ਹੈ ਅਤੇ ਡਿਜੀਟਲ ਗ੍ਰਿਫ਼ਤਾਰੀ ਵਾਰੰਟ ਜਾਰੀ ਹੋਇਆ ਹੈ। ਵੀਡੀਓ ਕਾਲ ਡਿਸਕਨੈਕਟ ਨਾ ਕਰੋ, ਕਿਸੇ ਨੂੰ ਨਾ ਦੱਸੋ।", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਸਾਡੇ ਵਿਆਹ ਦਾ ਕਾਰਡ ਵੇਖਣ ਲਈ wedding_card.pdf.apk ਡਾਊਨਲੋਡ ਕਰੋ ਅਤੇ ਆਸ਼ੀਰਵਾਦ ਦਿਓ।", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਐਸਬੀਆਈ ਗਾਹਕ: ਤੁਹਾਡਾ ਖਾਤਾ ੨੪ ਘੰਟਿਆਂ ਵਿੱਚ ਬੰਦ ਹੋ ਜਾਵੇਗਾ। ਕੇਵਾਈਸੀ ਅਪਡੇਟ ਕਰਨ ਲਈ ਲਿੰਕ ਤੇ ਕਲਿੱਕ ਕਰੋ: http://sbi-kyc-verify.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ਟਰੈਫਿਕ ਪੁਲਿਸ: ਤੁਹਾਡੀ ਗੱਡੀ ਦਾ ਟਰੈਫਿਕ ਚਲਾਨ ਬਕਾਇਆ ਹੈ। ਚਲਾਨ ਵੇਖਣ ਲਈ echallan.apk ਡਾਊਨਲੋਡ ਕਰੋ। Ref: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ਵਧਾਈਆਂ! ₹{amt} ਕੈਸ਼ਬੈਕ ਮਨਜ਼ੂਰ ਹੋਇਆ ਹੈ। ਪੈਸੇ ਲੈਣ ਲਈ ਪਿੰਨ ਪਾਓ।", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਬੈਂਕ ਮੈਨੇਜਰ ਬੋਲ ਰਿਹਾ ਹਾਂ, ਖਾਤਾ ਚਾਲੂ ਰੱਖਣ ਲਈ ਓਟੀਪੀ ਦਿਓ।", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਬਿਜਲੀ ਵਿਭਾਗ: ਗਲਤ ਬਿੱਲ ਠੀਕ ਕਰਨ ਲਈ ਹੁਣੇ AnyDesk ਐਪ ਇੰਸਟਾਲ ਕਰੋ।", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਘਰ ਬੈਠੇ ਪਾਰਟ ਟਾਈਮ ਕੰਮ ਕਰਕੇ ਰੋਜ਼ਾਨਾ ₹੩੦੦੦ ਕਮਾਓ। ਯੂਟਿਊਬ ਵੀਡੀਓ ਲਾਈਕ ਕਰਨ ਲਈ ਟੈਲੀਗ੍ਰਾਮ ਗਰੁੱਪ ਵਿੱਚ ਸ਼ਾਮਲ ਹੋਵੋ: http://t.me/task123", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਪੀਐਮ ਮੁਦਰਾ ਯੋਜਨਾ ਅਧੀਨ ₹੫,੦੦,੦੦੦ ਕਰਜ਼ਾ ਮਨਜ਼ੂਰ ਹੋਇਆ। ਪ੍ਰੋਸੈਸਿੰਗ ਫੀਸ ਦਿਓ ਇਸ ਲਿੰਕ ਤੇ: http://pm-loan-charge.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ਇੰਡੀਆ ਪੋਸਟ: ਤੁਹਾਡਾ ਪਾਰਸਲ ਡਿਲੀਵਰੀ ਫੇਲ੍ਹ ਹੋ ਗਿਆ। ਪਤਾ ਅਪਡੇਟ ਕਰਨ ਲਈ ਲਿੰਕ ਖੋਲ੍ਹੋ: http://indiapost-update.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ਇਹ ਮੇਰਾ ਨਵਾਂ ਨੰਬਰ ਹੈ, ਹਸਪਤਾਲ ਵਿੱਚ ਹਾਂ ਤੁਰੰਤ ਪੈਸੇ ਭੇਜੋ।", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਐਚਡੀਐਫਸੀ ਚੇਤਾਵਨੀ: ਤੁਹਾਡਾ ਕਾਰਡ ਬਲਾਕ ਹੋ ਗਿਆ ਹੈ। ਵੇਰੀਫਾਈ ਕਰਨ ਲਈ ਕਲਿੱਕ ਕਰੋ: http://hdfc-update.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ਵਧਾਈਆਂ ਤੁਸੀਂ ₹੨੫ ਲੱਖ ਦੀ ਲਾਟਰੀ ਜਿੱਤੀ ਹੈ। ਇਨਾਮ ਲੈਣ ਲਈ ਰਜਿਸਟ੍ਰੇਸ਼ਨ ਫੀਸ ਜਮ੍ਹਾਂ ਕਰੋ।", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਪੀਐਸਪੀਸੀਐਲ ਬਿਜਲੀ: ਬਿੱਲ ਨਾ ਭਰਨ ਤੇ ਤੁਰੰਤ ਬਿਜਲੀ ਕੱਟ ਦਿੱਤੀ ਜਾਵੇਗੀ: http://pspcl-pay.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

DEV_BENIGN_PA = [
    ("Dear Consumer, your PSPCL bill of Rs {amt} for Account No 3001234567 is generated. Pay online at https://pspcl.in - PSPCL", "UTILITY", "DLT_HEADER", "SMS", "PB-PSPCL-G"),
    ("ਤੁਹਾਡੇ A/c XX4321 ਵਿੱਚੋਂ INR {amt}.00 {date} ਨੂੰ ਡੈਬਿਟ ਹੋਏ ਹਨ। ਹੈਲਪਲਾਈਨ: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 45,000.00 credited to A/c XX9812 on {date}. Bal: INR 52,100.00. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("ਨੈੱਟਬੈਂਕਿੰਗ ਲੌਗਇਨ ਲਈ ਤੁਹਾਡਾ ਓਟੀਪੀ 123456 ਹੈ। ਇਹ ਓਟੀਪੀ ਕਿਸੇ ਨਾਲ ਸਾਂਝਾ ਨਾ ਕਰੋ। - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ਤੁਹਾਡਾ ਏਅਰਟੈੱਲ ਪ੍ਰੀਪੇਡ ਰੀਚਾਰਜ ਸਫਲ ਹੋ ਗਿਆ ਹੈ। ਵੈਧਤਾ ੨੮ ਦਿਨ। ਧੰਨਵਾਦ। - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("ਪੰਜਾਬ ਪੁਲਿਸ: ਕਿਸੇ ਵੀ ਅਣਜਾਣ ਵਿਅਕਤੀ ਨਾਲ ਓਟੀਪੀ ਜਾਂ ਬੈਂਕ ਵੇਰਵੇ ਸਾਂਝੇ ਨਾ ਕਰੋ। ਸੁਚੇਤ ਰਹੋ।", "GOVERNMENT", "DLT_HEADER", "SMS", "PB-PUPOL-G"),
    ("ਵੈਸਾਖੀ ਦੀਆਂ ਲੱਖ-ਲੱਖ ਵਧਾਈਆਂ! ਗੁਰੂ ਸਾਹਿਬ ਤੁਹਾਡੇ ਪਰਿਵਾਰ ਤੇ ਮਿਹਰ ਭਰਿਆ ਹੱਥ ਰੱਖਣ।", "CHAT", "NAMED", "WHATSAPP", "Gurpreet"),
    ("ਅੱਜ ਸ਼ਾਮ ਨੂੰ ਸਾਰੇ ਇਕੱਠੇ ਖਾਣਾ ਖਾਣ ਚੱਲਾਂਗੇ। ੭ ਵਜੇ ਤਿਆਰ ਰਹੀਂ।", "CHAT", "NAMED", "WHATSAPP", "Harpreet"),
    ("ਅੰਮ੍ਰਿਤਸਰ ਤੋਂ ਦਿੱਲੀ ਦੀ ਰੇਲ ਟਿਕਟ ਬੁੱਕ ਹੋ ਗਈ ਹੈ। ਸੀਟ ਨੰਬਰ {ref}।", "CHAT", "NAMED", "WHATSAPP", "Manpreet"),
    ("ਕੱਲ੍ਹ ਦੀ ਮੀਟਿੰਗ ਲਈ ਪ੍ਰੈਜ਼ੈਂਟੇਸ਼ਨ ਫਾਈਲ ਤਿਆਰ ਕਰ ਲਈ ਹੈ, ਇੱਕ ਵਾਰ ਦੇਖ ਲਈਂ।", "CHAT", "NAMED", "WHATSAPP", "Simran"),
    ("ਬਾਜ਼ਾਰੋਂ ਵਾਪਸ ਆਉਂਦੇ ਸਮੇਂ ਸਬਜ਼ੀਆਂ ਅਤੇ ਫਲ ਲੈ ਆਵੀਂ।", "CHAT", "NAMED", "WHATSAPP", "Mataji"),
    ("ਡਾਕਟਰ ਦੀ ਅਪਾਇੰਟਮੈਂਟ ਕੱਲ੍ਹ ਸ਼ਾਮ ੫ ਵਜੇ ਹੈ, ਪਰਚੀ ਨਾਲ ਲੈ ਲਈਂ।", "CHAT", "NAMED", "WHATSAPP", "Pitaji"),
    ("ਪ੍ਰੀਖਿਆ ਦਾ ਨਤੀਜਾ ਅੱਜ ਦੁਪਹਿਰ ਆਵੇਗਾ, ਚਿੰਤਾ ਨਾ ਕਰ ਚੰਗਾ ਹੀ ਆਊ।", "CHAT", "NAMED", "WHATSAPP", "Aman"),
    ("ਜਨਮਦਿਨ ਦੀਆਂ ਬਹੁਤ ਬਹੁਤ ਮੁਬਾਰਕਾਂ ਵੀਰੇ! ਜਿਊਂਦਾ ਵੱਸਦਾ ਰਹਿ।", "CHAT", "NAMED", "WHATSAPP", "Navjot"),
    ("ਉਹ ਨਵਾਂ ਪੰਜਾਬੀ ਨਾਵਲ ਪੜ੍ਹ ਕੇ ਖ਼ਤਮ ਕੀਤਾ, ਬਹੁਤ ਕਮਾਲ ਦੀ ਕਹਾਣੀ ਹੈ।", "CHAT", "NAMED", "WHATSAPP", "Kiran"),
    ("ਅੱਜ ਲੁਧਿਆਣੇ ਵਿੱਚ ਬਹੁਤ ਮੀਂਹ ਪੈ ਰਿਹਾ ਹੈ, ਬਾਹਰ ਜਾਣ ਵੇਲੇ ਛਤਰੀ ਜ਼ਰੂਰ ਲੈ ਲਈਂ।", "CHAT", "NAMED", "WHATSAPP", "Rani"),
    ("Your Amazon delivery agent is out for delivery. Track at https://amazon.in", "DELIVERY", "DLT_HEADER", "SMS", "AD-AMAZON-T"),
    ("ਕੱਲ੍ਹ ਵਾਲਾ ਕ੍ਰਿਕਟ ਮੈਚ ਭਾਰਤ ਨੇ ਬਹੁਤ ਵਧੀਆ ਜਿੱਤਿਆ! ਤੂੰ ਵੇਖਿਆ ਸੀ?", "CHAT", "NAMED", "WHATSAPP", "Jagjit"),
    ("ਅਗਲੇ ਐਤਵਾਰ ਪੁਰਾਣੇ ਸਕੂਲੀ ਦੋਸਤਾਂ ਦਾ ਮੇਲ-ਮਿਲਾਪ ਰੱਖਿਆ ਹੈ, ਤੂੰ ਜ਼ਰੂਰ ਆਉਣਾ।", "CHAT", "NAMED", "WHATSAPP", "Amrit"),
    ("ਅੱਜ ਦੁਪਹਿਰੇ ਮੱਕੀ ਦੀ ਰੋਟੀ ਤੇ ਸਰ੍ਹੋਂ ਦਾ ਸਾਗ ਬਣਾਇਆ ਹੈ, ਖਾਣ ਆ ਜਾ।", "CHAT", "NAMED", "WHATSAPP", "Chachiji"),
    ("ਬੈਂਕ ਸੁਰੱਖਿਆ ਸਲਾਹ: ਪੈਸੇ ਲੈਣ ਲਈ ਪਿੰਨ ਦੀ ਲੋੜ ਨਹੀਂ। ਪਿੰਨ ਸਿਰਫ਼ ਪੈਸੇ ਭੇਜਣ ਲਈ ਹੁੰਦਾ ਹੈ। - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ਐਸਬੀਆਈ ਲੋਨ ਆਫਰ: ਤੁਹਾਡੇ ਲਈ ₹੫,੦੦,੦੦੦ ਪਰਸਨਲ ਲੋਨ ਉਪਲਬਧ ਹੈ। ਪ੍ਰੋਸੈਸਿੰਗ ਫੀਸ ₹੧,੦੦੦। ਅਪਲਾਈ ਕਰੋ: https://sbi.co.in - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T")
]

TEST_SCAM_PA = [
    ("ਜ਼ਰੂਰੀ ਸੂਚਨਾ: ਬਿਜਲੀ ਬਿੱਲ ਬਕਾਇਆ ਹੋਣ ਕਾਰਨ ਅੱਜ ਰਾਤ ਬਿਜਲੀ ਬੰਦ ਹੋਵੇਗੀ। ਸੰਪਰਕ ਕਰੋ: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਦਿੱਲੀ ਸਾਈਬਰ ਪੁਲਿਸ: ਤੁਹਾਡੇ ਖਾਤੇ ਵਿੱਚ ਗੈਰ-ਕਾਨੂੰਨੀ ਲੈਣ-ਦੇਣ ਮਿਲਿਆ ਹੈ। ਵੀਡੀਓ ਕਾਲ ਤੇ ਰਹੋ, ਕੱਟੋ ਨਾ।", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਵਿਆਹ ਦਾ ਸੱਦਾ ਪੱਤਰ ਵੇਖਣ ਲਈ invite.apk ਇੰਸਟਾਲ ਕਰੋ।", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਐਸਬੀਆਈ ਚੇਤਾਵਨੀ: ਕੇਵਾਈਸੀ ਨਾ ਹੋਣ ਕਾਰਨ ਖਾਤਾ ਮੁਅੱਤਲ ਕੀਤਾ ਗਿਆ: http://sbi-unblock.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ਟਰੈਫਿਕ ਚਲਾਨ: ਤੁਹਾਡੇ ਵਾਹਨ ਤੇ ਜੁਰਮਾਨਾ ਹੋਇਆ ਹੈ। ਚਲਾਨ ਵੇਖੋ challan.apk ਡਾਊਨਲੋਡ ਕਰਕੇ।", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ਤੁਹਾਨੂੰ ₹{amt} ਦਾ ਰਿਵਾਰਡ ਮਿਲਿਆ ਹੈ। ਪੈਸੇ ਪ੍ਰਾਪਤ ਕਰਨ ਲਈ ਯੂਪੀਆਈ ਪਿੰਨ ਦਰਜ ਕਰੋ।", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਬੈਂਕ ਵੈਰੀਫਿਕੇਸ਼ਨ ਲਈ ਆਇਆ ਓਟੀਪੀ ਕੋਡ ਭੇਜੋ।", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਬਿਜਲੀ ਬਿੱਲ ਠੀਕ ਕਰਨ ਲਈ TeamViewer ਡਾਊਨਲੋਡ ਕਰੋ।", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਯੂਟਿਊਬ ਵੀਡੀਓ ਲਾਈਕ ਕਰਕੇ ਰੋਜ਼ ਕਮਾਓ। ਟੈਲੀਗ੍ਰਾਮ ਚੈਨਲ ਤੇ ਆਓ: http://t.me/earn_now", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਤੁਰੰਤ ਕਰਜ਼ਾ ਮਨਜ਼ੂਰ: ₹੨,੦੦,੦੦੦ ਲਈ ਫਾਈਲ ਚਾਰਜ ਭੇਜੋ: http://quick-loan.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ਪਾਰਸਲ ਰੁਕ ਗਿਆ ਹੈ। ਸਹੀ ਪਤਾ ਦੇਣ ਲਈ ਲਿੰਕ ਖੋਲ੍ਹੋ: http://speedpost-service.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ਮੈਂ ਮੁਸੀਬਤ ਵਿੱਚ ਹਾਂ, ਇਹ ਮੇਰਾ ਨਵਾਂ ਨੰਬਰ ਹੈ, ਤੁਰੰਤ ₹੫੦੦੦ ਭੇਜੋ।", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਪੈਨ ਲਿੰਕ ਨਹੀਂ ਹੈ, ਤੁਹਾਡਾ ਖਾਤਾ ਬੰਦ ਹੋ ਜਾਵੇਗਾ। ਅਪਡੇਟ ਕਰੋ: http://pan-update.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ਤੁਸੀਂ ₹੧੦ ਲੱਖ ਦਾ ਲੱਕੀ ਡਰਾਅ ਜਿੱਤਿਆ ਹੈ। ਟੈਕਸ ਫੀਸ ਜਮ੍ਹਾਂ ਕਰੋ।", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ਬਿਜਲੀ ਕੱਟਣ ਤੋਂ ਬਚਣ ਲਈ ਹੁਣੇ ਬਿੱਲ ਭਰੋ: http://bijli-bill.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST_BENIGN_PA = [
    ("PSPCL Power: Scheduled maintenance in your area on {date} from 10 AM to 2 PM. - PSPCL", "UTILITY", "DLT_HEADER", "SMS", "PB-PSPCL-G"),
    ("ਤੁਹਾਡੇ A/c XX8765 ਵਿੱਚ INR {amt}.00 {date} ਨੂੰ ਜਮ੍ਹਾਂ ਹੋਏ ਹਨ। - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Transaction of INR {amt}.00 completed via HDFC NetBanking on {date}. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("456789 ਐਸਬੀਆਈ ਕਾਰਡ ਭੁਗਤਾਨ ਲਈ ਪੁਸ਼ਟੀਕਰਨ ਕੋਡ ਹੈ। - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ਜੀਓ ਵੈਲਕਮ ਆਫਰ ਸਰਗਰਮ ਹੈ। ਅਸੀਮਤ 5G ਡੇਟਾ ਦਾ ਆਨੰਦ ਲਓ। - Jio", "TELECOM", "DLT_HEADER", "SMS", "JX-JIOINF-P"),
    ("ਸਾਈਬਰ ਸੁਰੱਖਿਆ: ਸ਼ੱਕੀ ਲਿੰਕਾਂ ਤੇ ਕਲਿੱਕ ਨਾ ਕਰੋ। ਹੈਲਪਲਾਈਨ 1930। - ਪੁਲਿਸ", "GOVERNMENT", "DLT_HEADER", "SMS", "PB-PUPOL-G"),
    ("ਦੀਵਾਲੀ ਅਤੇ ਬੰਦੀ ਛੋੜ ਦਿਵਸ ਦੀਆਂ ਲੱਖ-ਲੱਖ ਵਧਾਈਆਂ!", "CHAT", "NAMED", "WHATSAPP", "Jaswinder"),
    ("ਕੱਲ੍ਹ ਸਵੇਰੇ ਸੈਰ ਕਰਨ ਚੱਲੀਏ?", "CHAT", "NAMED", "WHATSAPP", "Satnam"),
    ("ਬੱਸ ਦੀ ਟਿਕਟ ਪੱਕੀ ਹੋ ਗਈ ਹੈ, ਸਮੇਂ ਸਿਰ ਅੱਡੇ ਪਹੁੰਚ ਜਾਈਂ।", "CHAT", "NAMED", "WHATSAPP", "Tarun"),
    ("ਪ੍ਰੋਜੈਕਟ ਰਿਪੋਰਟ ਤਿਆਰ ਹੈ, ਇੱਕ ਵਾਰ ਚੈੱਕ ਕਰ ਲਓ।", "CHAT", "NAMED", "WHATSAPP", "Sukhdev"),
    ("ਘਰ ਆਉਂਦੇ ਸਮੇਂ ਦੁੱਧ ਅਤੇ ਬਰੈੱਡ ਲੈ ਆਉਣਾ।", "CHAT", "NAMED", "WHATSAPP", "Mataji"),
    ("ਦਵਾਈਆਂ ਵੇਲੇ ਸਿਰ ਲੈਂਦੇ ਰਹੋ।", "CHAT", "NAMED", "WHATSAPP", "Pitaji"),
    ("ਕਾਲਜ ਦਾ ਦਾਖ਼ਲਾ ਫਾਰਮ ਜਮ੍ਹਾਂ ਹੋ ਗਿਆ ਹੈ।", "CHAT", "NAMED", "WHATSAPP", "Baljit"),
    ("ਵਿਆਹ ਦੀ ਵਰ੍ਹੇਗੰਢ ਦੀਆਂ ਬਹੁਤ-ਬਹੁਤ ਮੁਬਾਰਕਾਂ!", "CHAT", "NAMED", "WHATSAPP", "Paramjit"),
    ("ਕਿਤਾਬ ਪੜ੍ਹ ਕੇ ਬਹੁਤ ਚੰਗਾ ਲੱਗਿਆ, ਬੜੀ ਗਿਆਨਵਾਨ ਹੈ।", "CHAT", "NAMED", "WHATSAPP", "Mandeep"),
    ("ਅੱਜ ਮੌਸਮ ਬਹੁਤ ਠੰਢਾ ਹੈ, ਗਰਮ ਕੱਪੜੇ ਪਾ ਕੇ ਰੱਖਿਓ।", "CHAT", "NAMED", "WHATSAPP", "Rupinder"),
    ("ਤੁਹਾਡਾ ਫਲਿੱਪਕਾਰਟ ਆਰਡਰ ਅੱਜ ਪਹੁੰਚੇਗਾ। ਟਰੈਕ ਕਰੋ: https://flipkart.com", "DELIVERY", "DLT_HEADER", "SMS", "FK-FLPKRT-T"),
    ("ਕਬੱਡੀ ਦਾ ਮੈਚ ਬੜਾ ਫਸਵਾਂ ਤੇ ਰੋਚਕ ਸੀ।", "CHAT", "NAMED", "WHATSAPP", "Kuldeep"),
    ("ਯੂਨੀਵਰਸਿਟੀ ਦੇ ਪੁਰਾਣੇ ਦੋਸਤ ਅਗਲੇ ਮਹੀਨੇ ਮਿਲ ਰਹੇ ਹਨ।", "CHAT", "NAMED", "WHATSAPP", "Davinder"),
    ("ਅੱਜ ਖੀਰ ਤੇ ਪੂੜੀਆਂ ਬਣਾਈਆਂ ਹਨ, ਖਾਣ ਆ ਜਾਓ।", "CHAT", "NAMED", "WHATSAPP", "Chachaji"),
    ("ਬੈਂਕ ਸਲਾਹ: ਪੈਸੇ ਲੈਣ ਲਈ ਪਿੰਨ ਨਹੀਂ ਲੱਗਦਾ। ਸੁਚੇਤ ਰਹੋ। - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ਐਸਬੀਆਈ ਕਾਰ ਲੋਨ: ਆਕਰਸ਼ਕ ਵਿਆਜ ਦਰ। ਪ੍ਰੋਸੈਸਿੰਗ ਫੀਸ ₹੫੦੦। ਵੇਰਵੇ: https://sbi.co.in - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T")
]

def generate_all():
    eval_dir = "eval"
    os.makedirs(eval_dir, exist_ok=True)

    configs = [
        ("gu", DEV_SCAM_GU, DEV_BENIGN_GU, TEST_SCAM_GU, TEST_BENIGN_GU),
        ("kn", DEV_SCAM_KN, DEV_BENIGN_KN, TEST_SCAM_KN, TEST_BENIGN_KN),
        ("ml", DEV_SCAM_ML, DEV_BENIGN_ML, TEST_SCAM_ML, TEST_BENIGN_ML),
        ("pa", DEV_SCAM_PA, DEV_BENIGN_PA, TEST_SCAM_PA, TEST_BENIGN_PA)
    ]

    summary = {}

    for lang, dev_scams, dev_benign, test_scams, test_benign in configs:
        # Generate dev: 60 scams (from 15 templates) + 100 benign (from 22 templates) = 160 rows (62.5% benign)
        dev_scam_rows = make_rows(dev_scams, "scam", lang, 60, f"dev-{lang}-scam", f"dev-{lang}-scam")
        dev_ben_rows = make_rows(dev_benign, "benign", lang, 100, f"dev-{lang}-ben", f"dev-{lang}-ben")
        dev_rows = dev_scam_rows + dev_ben_rows
        random.shuffle(dev_rows)

        # Generate test: 60 scams (from 15 templates) + 100 benign (from 22 templates) = 160 rows (62.5% benign)
        test_scam_rows = make_rows(test_scams, "scam", lang, 60, f"test-{lang}-scam", f"test-{lang}-scam")
        test_ben_rows = make_rows(test_benign, "benign", lang, 100, f"test-{lang}-ben", f"test-{lang}-ben")
        test_rows = test_scam_rows + test_ben_rows
        random.shuffle(test_rows)

        dev_path = os.path.join(eval_dir, f"dev_{lang}.jsonl")
        test_path = os.path.join(eval_dir, f"test_{lang}.jsonl")

        with open(dev_path, "w", encoding="utf-8") as f:
            for r in dev_rows:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")

        if not os.path.exists(test_path):
            with open(test_path, "w", encoding="utf-8") as f:
                for r in test_rows:
                    f.write(json.dumps(r, ensure_ascii=False) + "\n")

        summary[lang] = {
            "dev_total": len(dev_rows),
            "dev_scam": len(dev_scam_rows),
            "dev_benign": len(dev_ben_rows),
            "dev_benign_pct": len(dev_ben_rows) / len(dev_rows) * 100,
            "dev_scam_templates": len(dev_scams),
            "dev_benign_templates": len(dev_benign),
            "test_total": len(test_rows),
            "test_scam": len(test_scam_rows),
            "test_benign": len(test_ben_rows),
            "test_benign_pct": len(test_ben_rows) / len(test_rows) * 100,
            "test_scam_templates": len(test_scams),
            "test_benign_templates": len(test_benign)
        }

    print("Generation complete! Summary:")
    print(json.dumps(summary, indent=2))

if __name__ == "__main__":
    generate_all()
