#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Gourav Mahunta

"""
tools/eval/generate_tier2_datasets.py
Generates Tier 2 evaluation splits (dev and test) for Bengali (bn), Marathi (mr),
Telugu (te), Tamil (ta), and Odia (or) with regional DLT headers and zero PII violations.
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
# 1. BENGALI (bn)
# ==========================================

DEV_SCAM_BN = [
    ("প্রিয় গ্রাহক, বকেয়া বিল জমা না দিলে আজ রাত ৯:৩০ টায় আপনার বিদ্যুৎ সংযোগ বিচ্ছিন্ন করা হবে। বিদ্যুৎ বিভাগ আধিকারিককে কল করুন: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("কলকাতা পুলিশ ক্রাইম ব্রাঞ্চ: আপনার আধার নম্বরে মাদক পার্সেল ধরা পড়েছে এবং ডিজিটাল গ্রেফতারি পরোয়ানা জারি হয়েছে। ভিডিও কল কাটবেন না, কাউকে জানাবেন না।", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("আমাদের বিয়ের কার্ড দেখতে wedding_card.pdf.apk ডাউনলোড করুন এবং আশীর্বাদ করুন।", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("এসবিআই গ্রাহক: আপনার অ্যাকাউন্ট বন্ধ হয়ে যাবে ২৪ ঘণ্টার মধ্যে। কেওয়াইসি আপডেট করতে লিঙ্কে ক্লিক করুন: http://sbi-kyc-verify.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("পরিবহন দপ্তর: আপনার গাড়ির ট্রাফিক চালান অনিষ্পন্ন আছে। চালান দেখতে echallan.apk ডাউনলোড করুন। Ref: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("অভিনন্দন! ₹{amt} টাকা ক্যাশব্যাক অনুমোদিত হয়েছে। টাকা গ্রহণ করতে ইউপিআই পিন লিখুন।", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ব্যাংক ম্যানেজার বলছি, আপনার অ্যাকাউন্ট চালু রাখতে অবিলম্বে ওটিপি দিন।", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("বিদ্যুৎ বিভাগ: ভুল বিল সংশোধনের জন্য এখনই AnyDesk অ্যাপ ইনস্টল করুন।", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ঘরে বসে পার্ট টাইম কাজ করে প্রতিদিন ৩০০০ টাকা আয় করুন। ইউটিউব ভিডিও লাইক করতে টেলিগ্রাম গ্রুপে যোগ দিন: http://t.me/task123", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("পিএম মুদ্রা যোজনায় ₹৫,০০,০০০ ঋণ অনুমোদিত হয়েছে। ফাইল চার্জ বাবদ ₹২০০০ এই লিঙ্কে পাঠান: http://pm-loan-charge.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ইন্ডিয়া পোস্ট: আপনার পার্সেল ডেলিভারি ব্যর্থ হয়েছে। ঠিকানা পরিবর্তন করতে লিঙ্কে যান: http://indiapost-update.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("এটা আমার নতুন নম্বর, হাসপাতালে আছি জরুরি টাকা দরকার, অবিলম্বে টাকা পাঠান।", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("এইচডিএফসি সতর্কতা: আপনার কার্ড স্থগিত করা হয়েছে। অবিলম্বে ভেরিফাই করতে ক্লিক করুন: http://hdfc-update.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("অভিনন্দন আপনি লটারি জিতেছেন ₹২৫ লক্ষ টাকা। পুরস্কার পেতে রেজিস্ট্রেশন ফি জমা দিন।", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("পশ্চিমবঙ্গ বিদ্যুৎ: বিল পরিশোধ না করলে অবিলম্বে সংযোগ কেটে দেওয়া হবে: http://wbsedcl-pay.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

DEV_BENIGN_BN = [
    ("Dear Consumer, your WBSEDCL bill of Rs {amt} for Cons ID 102938475 is generated. Pay online at https://wbsedcl.in - WBSEDCL", "UTILITY", "DLT_HEADER", "SMS", "WB-WBSEDCL-G"),
    ("আপনার A/c XX4321 থেকে INR {amt}.00 ডেবিট হয়েছে {date} তারিখে। হেল্পলাইন: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 45,000.00 credited to A/c XX9812 on {date}. Bal: INR 52,100.00. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("123456 হল আপনার ওটিপি নেটব্যাঙ্কিং লগইনের জন্য। এই ওটিপি কোড কাউকে জানাবেন না। - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("আপনার এয়ারটেল প্রিপেইড রিচার্জ সফল হয়েছে। বৈধতা ২৮ দিন। ধন্যবাদ। - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("কলকাতা পুলিশ: কোনো অচেনা ব্যক্তিকে ওটিপি বা ব্যাঙ্কের গোপন তথ্য শেয়ার করবেন না। সতর্ক থাকুন।", "GOVERNMENT", "DLT_HEADER", "SMS", "VK-KOLPOL-G"),
    ("শুভ শারদীয়ার প্রীতি ও শুভেচ্ছা! পরিবারের সবাইকে নিয়ে পুজো খুব ভালো কাটুক।", "CHAT", "NAMED", "WHATSAPP", "Dipankar"),
    ("আজ সন্ধ্যায় সবাই মিলে বাইরে খেতে যাব। তুই ঠিক ৭টায় রেডি থাকিস।", "CHAT", "NAMED", "WHATSAPP", "Pooja"),
    ("দীঘা যাওয়ার ট্রেনের টিকিট কাটা হয়ে গেছে। সকাল ৬টার ট্রেন। সিট নম্বর {ref}।", "CHAT", "NAMED", "WHATSAPP", "Sourav"),
    ("কালকের মিটিংয়ের জন্য প্রেজেন্টেশন স্লাইডগুলো রেডি করে রেখেছি। একবার দেখে নিস।", "CHAT", "NAMED", "WHATSAPP", "Amit"),
    ("বাজার থেকে ফেরার সময় একটু মিষ্টি আর ফল নিয়ে আসিস।", "CHAT", "NAMED", "WHATSAPP", "Maa"),
    ("ডাক্তারের অ্যাপয়েন্টমেন্ট কাল বিকেল ৫টায় আছে, প্রেসক্রিপশনটা সাথে নিস।", "CHAT", "NAMED", "WHATSAPP", "Baba"),
    ("পরীক্ষার ফলাফল আজ দুপুর ১২টায় দেবে। ভালো রেজাল্ট হবে আশা করি।", "CHAT", "NAMED", "WHATSAPP", "Rahul"),
    ("শুভ জন্মদিন শুভঙ্কর! আগামী বছরটা তোর খুব ভালো কাটুক।", "CHAT", "NAMED", "WHATSAPP", "Suman"),
    ("বইমেলা থেকে যে উপন্যাসটা এনেছিলাম সেটা শেষ করলাম, দারুন লেখা!", "CHAT", "NAMED", "WHATSAPP", "Ananya"),
    ("আজ কলকাতায় খুব বৃষ্টি হচ্ছে, বেরোনোর সময় ছাতা নিতে ভুলিস না।", "CHAT", "NAMED", "WHATSAPP", "Rupa"),
    ("Your Amazon delivery agent is out for delivery. Track at https://amazon.in", "DELIVERY", "DLT_HEADER", "SMS", "AD-AMAZON-T"),
    ("মোহনবাগান আজকের ম্যাচটা দারুণ খেলল! তুই খেলাটা দেখলি?", "CHAT", "NAMED", "WHATSAPP", "Debasish"),
    ("কলেজের বন্ধুদের রিইউনিয়ন পরের রবিবার গঙ্গার ঘাটের কাছে হবে। তুই আসছিস তো?", "CHAT", "NAMED", "WHATSAPP", "Pritam"),
    ("আজ দুপুরে ইলিশ মাছের পাতুড়ি আর খিচুড়ি বানিয়েছি, খেয়ে খুব ভালো লাগল।", "CHAT", "NAMED", "WHATSAPP", "Kakima"),
    ("ব্যাংক নিরাপত্তা বার্তা: টাকা গ্রহণ করার জন্য কখনো ইউপিআই পিন দেওয়ার প্রয়োজন নেই। পিন শুধুমাত্র টাকা পাঠানোর জন্য। - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("এসবিআই লোন অফার: আপনার জন্য ₹৫,০০,০০০ পার্সোনাল লোন উপলব্ধ। প্রসেসিং ফি ₹১,০০০। আবেদন করুন: https://sbi.co.in - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T")
]

TEST_SCAM_BN = [
    ("জরুরি নোটিশ: আপনার বিদ্যুৎ বিল বাকি আছে, আজ রাতের মধ্যে বিল না দিলে সংযোগ বাতিল হবে। যোগাযোগ: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("সিবিআই হেডকোয়ার্টার: আপনার নামে অবৈধ মানি লন্ডারিং তদন্ত চলছে। কল কাটবেন না এবং লাইনে থাকুন।", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("আমাদের গৃহপ্রবেশ অনুষ্ঠানের নিমন্ত্রণপত্র card_invitation.apk ডাউনলোড করে দেখে নিন।", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("পাঞ্জাব ন্যাশনাল ব্যাংক: প্যান কার্ড লিঙ্ক না থাকায় অ্যাকাউন্ট স্থগিত। আপডেট করুন: http://pnb-verify-pan.buzz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ট্রাফিক জরিমানা বাকি। echallan_report.apk ইনস্টল করে জরিমানা পরিশোধ করুন। চালানের আইডি: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ফোনপে রিওয়ার্ড: ₹{amt} টাকা পেতে কিউআর স্ক্যান করে ইউপিআই পিন লিখুন।", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("সিম কার্ড ভেরিফিকেশনের জন্য আপনার মোবাইলে আসা ওটিপি পাঠান এখনই।", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ব্যাংক সাপোর্ট: সার্ভার ত্রুটি ঠিক করার জন্য টিমভিউয়ার ইনস্টল করুন অবিলম্বে।", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("দৈনিক ২০০০ টাকা ইনকাম করুন ঘরে বসে। টেলিগ্রাম চ্যানেলে আসুন: http://t.me/earn_daily", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("প্রধানমন্ত্রী মুদ্রা যোজনা: সহজ কিস্তিতে ঋণ মঞ্জুর। প্রসেসিং ফি দিন এই লিঙ্কে: http://mudra-scheme.site", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("আপনার কুরিয়ার ডেলিভারি স্থগিত আছে। অবিলম্বে ঠিকানা আপডেট করুন: http://courier-post.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("বিপদে পড়েছি টাকা চাই, এটা নতুন সিম, অবিলম্বে টাকা পাঠান।", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("আইসিআইসিআই অ্যালার্ট: আপনার ডেবিট কার্ড ব্লক হয়েছে। চালু করতে ক্লিক করুন: http://icici-card-unblock.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("লাকি ড্র বিজয়ী! আপনি পুরস্কার জিতেছেন। অগ্রিম টাকা দিন এই লিঙ্কে: http://winner-gift.click", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("বিদ্যুৎ নিগম: আজ রাত বিদ্যুৎ বিচ্ছিন্ন করা হবে বিল না দেওয়ার কারণে: http://power-wb.link", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST_BENIGN_BN = [
    ("Payment of Rs {amt} received for Consumer ID 987654321 on {date}. Official portal: https://wbsedcl.in", "UTILITY", "DLT_HEADER", "SMS", "WB-WBSEDCL-G"),
    ("Dear Customer, INR {amt}.00 debited from A/c XX5678 on {date}. Balance: INR 18,320.00. - PNB", "BANK", "DLT_HEADER", "SMS", "VM-PNBBNK-T"),
    ("Dear Customer, INR 3,000.00 credited to A/c XX4321 via UPI. Helpline: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("654321 is your login OTP for MyJio. Do not share this OTP code with anyone. - Jio", "OTP", "DLT_HEADER", "SMS", "JM-JIOINF-P"),
    ("Your Vi unlimited pack expires in 2 days. Recharge at https://myvi.in - Vi", "TELECOM", "DLT_HEADER", "SMS", "JD-VILNOT-P"),
    ("এসবিআই নিরাপত্তা বার্তা: সতর্ক থাকুন, ব্যাঙ্ক কখনো পিন বা পাসওয়ার্ড জানতে চায় না।", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("শুভ নববর্ষের আন্তরিক প্রীতি ও শুভেচ্ছা! নতুন বছর সকলের জন্য সুখ ও সমৃদ্ধি বয়ে আনুক।", "CHAT", "NAMED", "WHATSAPP", "Abhijit"),
    ("এই শনিবারে শান্তিনিকেতন যাওয়ার পরিকল্পনা করছি, তোরা কেউ যাবি?", "CHAT", "NAMED", "WHATSAPP", "Tanmoy"),
    ("আজ ফেরার পথে চাল, ডাল আর সরষের তেল নিয়ে আসিস।", "CHAT", "NAMED", "WHATSAPP", "Maa"),
    ("কাল বিকেলে নন্দন-এ নতুন বাংলা সিনেমাটা দেখতে যাব, টিকিট বুক করে রেখেছি।", "CHAT", "NAMED", "WHATSAPP", "Mousumi"),
    ("সেমিস্টারের রুটিন দিয়ে দিয়েছে, আগামী মাসের ১০ তারিখ থেকে পরীক্ষা শুরু।", "CHAT", "NAMED", "WHATSAPP", "Rohan"),
    ("বাবার ব্লাড টেস্টের রিপোর্ট ভালো এসেছে, ডাক্তার ওষুধ কমাতে বলেছেন।", "CHAT", "NAMED", "WHATSAPP", "Didi"),
    ("কাল রবীন্দ্র সদনে শাস্ত্রীয় সঙ্গীতের অনুষ্ঠান আছে, চল একসাথে যাই।", "CHAT", "NAMED", "WHATSAPP", "Indranil"),
    ("পুজোর ছুটিতে দার্জিলিং যাওয়ার ট্রেনের টিকিট পাওয়া গেছে। বুকিং কোড {ref}।", "CHAT", "NAMED", "WHATSAPP", "Bhai"),
    ("আজ বাড়িতে রসগোল্লা আর পায়েস বানিয়েছি, তোরা বিকেলে খেতে আয়।", "CHAT", "NAMED", "WHATSAPP", "Mashima"),
    ("কাল সকাল ৬টায় লেকে হাঁটতে যাবি? একটু তাড়াতাড়ি উঠিস।", "CHAT", "NAMED", "WHATSAPP", "Subhash"),
    ("Your Flipkart package has been dispatched. Track on https://flipkart.com", "DELIVERY", "DLT_HEADER", "SMS", "AD-FLPKRT-T"),
    ("ছাদে নতুন গোলাপ আর জবা ফুলের গাছ লাগালাম, খুব সুন্দর ফুল ফুটেছে।", "CHAT", "NAMED", "WHATSAPP", "Boudi"),
    ("আজ অফিসে লাঞ্চের সময় ক্যান্টিনে দেখা করছি।", "CHAT", "NAMED", "WHATSAPP", "Sagnik"),
    ("পুরনো বন্ধুদের ছবিগুলো দেখে খুব ভালো লাগল, দিনগুলো কত সুন্দর ছিল!", "CHAT", "NAMED", "WHATSAPP", "Sharmila")
]

# ==========================================
# 2. MARATHI (mr)
# ==========================================

DEV_SCAM_MR = [
    ("प्रिय ग्राहक, आपले महावितरण वीज बिल थकल्यामुळे आज रात्री ९:३० वाजता वीज पुरवठा खंडित केला जाईल. अधिकाऱ्यांशी संपर्क साधा: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("मुंबई पोलीस क्राईम ब्रांच: आपल्या आधार कार्डवर अमली पदार्थांचे पार्सल सापडले असून अटक वॉरंट जारी झाले आहे. व्हिडिओ कॉल डिस्कनेक्ट करू नका, कोणालाही सांगू नका.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("लग्नाची पत्रिका पाहण्यासाठी wedding_invitation.pdf.apk डाउनलोड करा.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("बँक ऑफ महाराष्ट्र: २४ तासांच्या आत खाते बंद होईल. केवायसी अपडेट करा: http://bom-kyc-verify.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("वाहतूक पोलीस: प्रलंबित चालान भरण्यासाठी echallan_pay.apk इन्स्टॉल करा. चालान क्र: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("अभिनंदन! ₹{amt} कॅशबॅक मंजूर. पैसे मिळवण्यासाठी पिन टाका.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("बँक मॅनेजर: आपले खाते सुरू ठेवण्यासाठी आलेला ओटीपी त्वरित सांगा.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("महावितरण वीज: चुकीचे बिल दुरुस्त करण्यासाठी अ‍ॅनीडेस्क इन्स्टॉल करा.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("घरबसल्या पार्ट टाइम काम करून दररोज ३००० रुपये कमवा. टेलिग्राम ग्रुपमध्ये सामील व्हा: http://t.me/task_mr", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("मुद्रा योजना: ₹५,००,००० कर्ज मंजूर झाले आहे. फाईल चार्ज म्हणून ₹२००० या लिंकवर पाठवा: http://mudra-loan.site", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("भारतीय टपाल: तुमचे पार्सल वितरित होऊ शकले नाही. पत्ता बदलण्यासाठी लिंकवर जा: http://indiapost-update.click", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("हा माझा नवीन नंबर आहे, दवाखान्यात आहे पैसे पाठवा तातडीने.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("एचडीएफसी बँक: पॅन कार्ड लिंक नसल्यामुळे खाते निलंबित. त्वरित लिंकवर क्लिक करा: http://hdfc-pan-update.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("अभिनंदन तुम्ही लॉटरी जिंकली आहे ₹१० लाख. बक्षीस मिळवण्यासाठी नोंदणी शुल्क भरा.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("महावितरण वीज: वीज तोडली जाईल त्वरित बिल भरा: http://msedcl-bill-pay.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

DEV_BENIGN_MR = [
    ("Dear Consumer, your MSEDCL bill for Cons No 019283746 is Rs {amt}. Pay via https://mahadiscom.in - MSEDCL", "UTILITY", "DLT_HEADER", "SMS", "MH-MSEDCL-G"),
    ("आपल्या खात्यातून INR {amt}.00 डेबिट झाले {date} रोजी. मदत क्रमांक: 18002583838 - Bank of Maharashtra", "BANK", "DLT_HEADER", "SMS", "MH-BOMBNK-T"),
    ("Dear Customer, INR 32,000.00 credited to A/c XX9812 on {date}. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("123456 हा तुमचा ओटीपी आहे. हा कोणालाही सांगू नका. - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("तुमचा जिओ रिचार्ज यशस्वी झाला आहे. वैधता २८ दिवस. धन्यवाद. - Jio", "TELECOM", "DLT_HEADER", "SMS", "JM-JIOINF-P"),
    ("महाराष्ट्र सायबर पोलीस: अनोळखी व्यक्तीसोबत ओटीपी किंवा बँक पासवर्ड शेअर करू नका. सतर्क राहा.", "GOVERNMENT", "DLT_HEADER", "SMS", "VK-MUMPLE-G"),
    ("गणेशोत्सवाच्या सर्वांना हार्दिक शुभेच्छा! बाप्पा आपल्या सर्व मनोकामना पूर्ण करोत.", "CHAT", "NAMED", "WHATSAPP", "Sachin"),
    ("आज संध्याकाळी आपण सगळे बाहेर जेवायला जाऊया. ७ वाजता तयार राहा.", "CHAT", "NAMED", "WHATSAPP", "Priya"),
    ("पुण्याला जाणाऱ्या शिवनेरी बसचे तिकीट बुक केले आहे. सकाळी ८ ची गाडी आहे.", "CHAT", "NAMED", "WHATSAPP", "Amol"),
    ("उद्याच्या ऑफिस प्रेझेंटेशनच्या स्लाईड्स पूर्ण झाल्या आहेत. एकदा तपासून घे.", "CHAT", "NAMED", "WHATSAPP", "Sneha"),
    ("बाजारातून येताना भाजी आणि फळे घेऊन ये आठवणीने.", "CHAT", "NAMED", "WHATSAPP", "Aai"),
    ("डॉक्टरांची अपॉइंटमेंट उद्या दुपारी ४ वाजता आहे, जुने रिपोर्ट सोबत घे.", "CHAT", "NAMED", "WHATSAPP", "Baba"),
    ("परीक्षेचा निकाल आज दुपारी जाहीर होणार आहे. खूप शुभेच्छा!", "CHAT", "NAMED", "WHATSAPP", "Rohan"),
    ("वाढदिवसाच्या खूप खूप शुभेच्छा राहुल! पुढील वर्ष उत्तम जावो.", "CHAT", "NAMED", "WHATSAPP", "Vikas"),
    ("सिंहगडावर ट्रेकिंगला जाण्याचा बेत खूप छान झाला, खूप मजा आली.", "CHAT", "NAMED", "WHATSAPP", "Prasad"),
    ("आज मुंबईत मुसळधार पाऊस सुरू आहे, बाहेर पडताना छत्री नक्की घे.", "CHAT", "NAMED", "WHATSAPP", "Neha"),
    ("Your Amazon package is out for delivery. Track at https://amazon.in", "DELIVERY", "DLT_HEADER", "SMS", "AD-AMAZON-T"),
    ("आजचा क्रिकेट सामना भारताने खूप चांगला जिंकला!", "CHAT", "NAMED", "WHATSAPP", "Sandeep"),
    ("शाळेतील मित्रांचे स्नेहसंमेलन पुढील रविवारी आयोजित केले आहे. तू येणार ना?", "CHAT", "NAMED", "WHATSAPP", "Kedar"),
    ("आज दुपारच्या जेवणात पुरणपोळी आणि कटाची आमटी बनवली आहे, जेवायला ये.", "CHAT", "NAMED", "WHATSAPP", "Kaku"),
    ("बँक सुरक्षा सूचना: पैसे मिळवण्यासाठी कधीही यूपीआय पिन टाकण्याची गरज नाही. पिन फक्त पैसे पाठवण्यासाठी असतो. - Bank of Maharashtra", "BANK", "DLT_HEADER", "SMS", "MH-BOMBNK-T"),
    ("बँक ऑफ महाराष्ट्र: आपल्यासाठी ₹३,००,००० वैयक्तिक कर्ज उपलब्ध आहे. प्रक्रिया शुल्क ₹१,००० लागू. अधिकृत संकेतस्थळावर अर्ज करा: https://bankofmaharashtra.in - BOM", "BANK", "DLT_HEADER", "SMS", "MH-BOMBNK-T")
]

TEST_SCAM_MR = [
    ("तातडीची सूचना: आपले वीज बिल प्रलंबित आहे, आज रात्री वीज बंद केली जाईल. संपर्क: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("सीबीआय कार्यालय: आपल्या नावाने बेकायदेशीर व्यवहार आढळले आहेत. कॉल कापू नका आणि कॉलवर राहा.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("आमच्या नवीन घराच्या वास्तुशांतीची पत्रिका card_vastu.apk डाऊनलोड करून पाहा.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("स्टेट बँक: केवायसी मुदत संपल्यामुळे खाते ब्लॉक केले जाईल. अपडेट करा: http://sbi-kyc-mr.buzz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ई-चालान भरण्यासाठी खालील अ‍ॅप डाऊनलोड करा: echallan_mumbai.apk. संदर्भ: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("गुगल पे बक्षीस: ₹{amt} स्वीकारण्यासाठी क्यूआर स्कॅन करून यूपीआय पिन टाका पैसे स्वीकारण्यासाठी.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("सिम कार्ड चालू ठेवण्यासाठी आलेला ओटीपी मला पाठवा.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("तांत्रिक अडचण सोडवण्यासाठी टीमव्ह्यूअर डाउनलोड करा त्वरित.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("दररोज २००० रुपये कमवा घरी बसून. टेलिग्राम चॅनेल जॉईन करा: http://t.me/earn_pune", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("पंतप्रधान मुद्रा कर्ज: कमी व्याजावर कर्ज मंजूर. प्रोसेसिंग फी भरा या लिंकवर: http://pm-mudra-online.site", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("तुमचे कुरिअर पोहोचू शकले नाही. त्वरित पत्ता अपडेट करा: http://delivery-track.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("अडचणीत आहे पैसे पाठवा, हा नवीन नंबर आहे, त्वरित ट्रान्सफर करा.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("आयसीआयसीआय बँक: आपले कार्ड ब्लॉक झाले आहे. सुरू करण्यासाठी लिंकवर क्लिक करा: http://icici-card-fix.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("लकी ड्रॉ विजेता! बक्षीस मिळवण्यासाठी आगाऊ रक्कम द्या या लिंकवर: http://lucky-draw-win.click", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("महावितरण: आज रात्री वीज कनेक्शन कट होईल त्वरित बिल भरा: http://mahavitaran-bill.link", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST_BENIGN_MR = [
    ("Payment of Rs {amt} received for Mahadiscom Consumer No 827192837 on {date}. Official: https://mahadiscom.in", "UTILITY", "DLT_HEADER", "SMS", "MH-MSEDCL-G"),
    ("Dear Customer, INR {amt}.00 debited from A/c XX4321 on {date}. Helpline: 18002583838 - Bank of Maharashtra", "BANK", "DLT_HEADER", "SMS", "MH-BOMBNK-T"),
    ("Dear Customer, INR 5,000.00 credited to A/c XX9812 via UPI. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("654321 हा तुमचा लॉगिन ओटीपी आहे. हा कोणाशीही शेअर करू नका. - Airtel", "OTP", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("तुमचा व्होडाफोन आयडिया पॅक संपत आला आहे. रिचार्ज करा: https://myvi.in - Vi", "TELECOM", "DLT_HEADER", "SMS", "JD-VILNOT-P"),
    ("बँकेकडून कधीही ओटीपी मागितला जात नाही. फसवणुकीपासून सावध राहा. - RBI Kehta Hai", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("गुढीपाडव्याच्या आणि मराठी नववर्षाच्या मनःपूर्वक हार्दिक शुभेच्छा!", "CHAT", "NAMED", "WHATSAPP", "Ganesh"),
    ("या शनिवारी महाबळेश्वरला जाण्याचे ठरवले आहे, तू येशील का?", "CHAT", "NAMED", "WHATSAPP", "Nilesh"),
    ("येताना दुकानातून गहू आणि तांदूळ घेऊन ये.", "CHAT", "NAMED", "WHATSAPP", "Aai"),
    ("उद्या संध्याकाळी बालगंधर्व मंदिरात नाटकाचा प्रयोग आहे, तिकीट काढले आहे.", "CHAT", "NAMED", "WHATSAPP", "Swati"),
    ("कॉलेजच्या सेमिस्टर परीक्षेचे वेळापत्रक आले आहे, तयारी सुरू कर.", "CHAT", "NAMED", "WHATSAPP", "Pooja"),
    ("आईची तब्येत आता खूप छान आहे, काळजी नको करू.", "CHAT", "NAMED", "WHATSAPP", "Tai"),
    ("उद्या सकाळी ६ वाजता टेकडीवर फिरायला जायचे आहे, लवकर उठ.", "CHAT", "NAMED", "WHATSAPP", "Mahesh"),
    ("दिवाळीच्या सुट्टीत कोकणात गावी जायचे नियोजन झाले आहे.", "CHAT", "NAMED", "WHATSAPP", "Dada"),
    ("आज घरी मोदक बनवले आहेत, संध्याकाळी नक्की ये.", "CHAT", "NAMED", "WHATSAPP", "Aatya"),
    ("नवीन घराचे काम खूप छान पूर्ण झाले आहे, फोटो पाठवले आहेत.", "CHAT", "NAMED", "WHATSAPP", "Sanjay"),
    ("Your Flipkart order has been shipped. Track at https://flipkart.com", "DELIVERY", "DLT_HEADER", "SMS", "AD-FLPKRT-T"),
    ("गच्चीवर छान फुलझाडे लावली आहेत, खूप छान फुले आली आहेत.", "CHAT", "NAMED", "WHATSAPP", "Shaila"),
    ("दुपारच्या जेवणासाठी ऑफिसच्या कॅन्टीनमध्ये भेटूया.", "CHAT", "NAMED", "WHATSAPP", "Ajit"),
    ("लहानपणीच्या आठवणी ताज्या झाल्या जुने फोटो पाहून.", "CHAT", "NAMED", "WHATSAPP", "Sunil")
]

# ==========================================
# 3. TELUGU (te)
# ==========================================

DEV_SCAM_TE = [
    ("ప్రియమైన వినియోగదారుడా, మీ విద్యుత్ బిల్లు చెల్లించనందున ఈ రాత్రి 9:30 గంటలకు విద్యుత్ సరఫరా నిలిపివేయబడుతుంది. అధికారిని సంప్రదించండి: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("హైదరాబాద్ పోలీస్ క్రైమ్ బ్రాంచ్: మీ ఆధార్ నంబర్‌పై మాదకద్రవ్యాల పార్శిల్ పట్టుబడింది మరియు అరెస్ట్ వారెంట్ జారీ చేయబడింది. వీడియో కాల్ కట్ చేయవద్దు, ఎవరికీ చెప్పవద్దు.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("మా పెళ్లి శుభలేఖ చూడటానికి wedding_invitation.apk డౌన్‌లోడ్ చేసుకోండి.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ఎస్బీఐ వినియోగదారుడు: 24 గంటల్లో మీ ఖాతా నిలిపివేయబడుతుంది. కేవైసీ అప్‌డేట్ చేయండి: http://sbi-kyc-telugu.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ట్రాఫిక్ చలాన్: మీ పెండింగ్ చలాన్ చెల్లించడానికి echallan_ts.apk ఇన్‌స్టాల్ చేయండి. రిఫరెన్స్: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("అభినందనలు! ₹{amt} క్యాష్‌బ్యాక్ ఆమోదించబడింది. డబ్బు పొందడానికి పిన్ ఎంటర్ చేయండి.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ఖాతా కొనసాగించడానికి మీ మొబైల్‌కు వచ్చిన ఓటీపీ చెప్పండి.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("విద్యుత్ శాఖ: బిల్లు సవరణ కోసం ఎనీడెస్క్ డౌన్‌లోడ్ చేయండి.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ఇంటి నుండే సంపాదించండి రోజుకు రూ. 3000. టెలిగ్రామ్ గ్రూప్‌లో చేరండి: http://t.me/earn_telugu", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("పిఎం ముద్ర యోజన: సులభ వాయిదాలలో రుణం మంజూరైంది. ఫైల్ చార్జ్ చెల్లించండి: http://mudra-loan-tg.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ఇండియా పోస్ట్: మీ పార్శిల్ డెలివరీ విఫలమైంది. చిరునామా మార్చడానికి లింక్‌ను సందర్శించండి: http://indiapost-update.site", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ఇది నా కొత్త నంబర్, హాస్పిటల్‌లో ఉన్నాను డబ్బులు పంపండి అత్యవసరంగా.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("హెచ్‌డిఎఫ్‌సి బ్యాంక్: పాన్ లింక్ కాలేదు కాబట్టి ఖాతా నిలిపివేయబడుతుంది. క్లిక్ చేయండి: http://hdfc-pan-tg.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("అభినందనలు మీరు లాటరీ గెలుచుకున్నారు రూ. 10 లక్షలు. రిజిస్ట్రేషన్ ఫీజు చెల్లించండి.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("తెలంగాణ విద్యుత్: కరెంట్ కట్ చేయబడుతుంది వెంటనే బిల్లు చెల్లించండి: http://tgspdcl-bill.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

DEV_BENIGN_TE = [
    ("Dear Consumer, your APCPDCL power bill of Rs {amt} is generated. Pay at https://apcpdcl.in - APCPDCL", "UTILITY", "DLT_HEADER", "SMS", "AP-APCPDC-G"),
    ("మీ ఖాతా XX4321 నుండి INR {amt}.00 డెబిట్ చేయబడింది {date} న. హెల్ప్‌లైన్: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 28,000.00 credited to A/c XX9812 on {date}. Bal: INR 35,200.00. - Union Bank", "BANK", "DLT_HEADER", "SMS", "UB-UBIBNK-T"),
    ("123456 మీ లాగిన్ ఓటీపీ. ఈ ఓటీపీని ఎవరితోనూ పంచుకోవద్దు. - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("మీ ఎయిర్‌టెల్ రీఛార్జ్ విజయవంతమైంది. వ్యాలిడిటీ 28 రోజులు. ధన్యవాదాలు. - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("సైబర్ క్రైమ్ పోలీస్: అనుమానాస్పద వ్యక్తులకు ఓటీపీ లేదా పాస్‌వర్డ్ చెప్పవద్దు. అప్రమత్తంగా ఉండండి.", "GOVERNMENT", "DLT_HEADER", "SMS", "TG-CYBPOL-G"),
    ("మీకు మరియు మీ కుటుంబ సభ్యులకు సంక్రాంతి శుభాకాంక్షలు! ఈ పండుగ మీ ఇంట ఆనందాన్ని నింపాలి.", "CHAT", "NAMED", "WHATSAPP", "Srinivas"),
    ("ఈ సాయంత్రం అందరం కలిసి భోజనానికి వెళ్దాం. 7 గంటలకు సిద్ధంగా ఉండండి.", "CHAT", "NAMED", "WHATSAPP", "Madhavi"),
    ("తిరుపతి వెళ్ళడానికి ట్రైన్ టికెట్లు బుక్ అయ్యాయి. ఉదయం 6 గంటల రైలు.", "CHAT", "NAMED", "WHATSAPP", "Venkatesh"),
    ("రేపటి ఆఫీస్ మీటింగ్ ప్రెజెంటేషన్ పూర్తయింది. ఒకసారి పరిశీలించండి.", "CHAT", "NAMED", "WHATSAPP", "Karthik"),
    ("మార్కెట్ నుండి వచ్చేటప్పుడు కూరగాయలు మరియు పండ్లు తీసుకురండి.", "CHAT", "NAMED", "WHATSAPP", "Amma"),
    ("డాక్టర్ అపాయింట్‌మెంట్ రేపు సాయంత్రం 5 గంటలకు ఉంది, రిపోర్టులు తీసుకురండి.", "CHAT", "NAMED", "WHATSAPP", "Nanna"),
    ("పరీక్ష ఫలితాలు ఈరోజు మధ్యాహ్నం విడుదలవుతాయి. ఆల్ ది బెస్ట్!", "CHAT", "NAMED", "WHATSAPP", "Anil"),
    ("పుట్టినరోజు శుభాకాంక్షలు ప్రశాంత్! ఈ సంవత్సరం అంతా బాగుండాలి.", "CHAT", "NAMED", "WHATSAPP", "Suresh"),
    ("హైదరాబాద్ బిర్యానీ చాలా బాగుంది, రెస్టారెంట్ అడ్రస్ పంపించాను.", "CHAT", "NAMED", "WHATSAPP", "Chaitanya"),
    ("ఈరోజు హైదరాబాద్‌లో భారీ వర్షం పడుతోంది, గొడుగు మర్చిపోవద్దు.", "CHAT", "NAMED", "WHATSAPP", "Swathi"),
    ("Your Amazon package is out for delivery. Track at https://amazon.in", "DELIVERY", "DLT_HEADER", "SMS", "AD-AMAZON-T"),
    ("ఈరోజు మ్యాచ్ ఇండియా అద్భుతంగా ఆడింది!", "CHAT", "NAMED", "WHATSAPP", "Kiran"),
    ("కాలేజీ మిత్రుల కలయిక వచ్చే ఆదివారం ప్లాన్ చేశాము. వస్తున్నావా?", "CHAT", "NAMED", "WHATSAPP", "Naresh"),
    ("ఈరోజు ఇంట్లో గారెలు మరియు పాయసం చేశాము, భోజనానికి రండి.", "CHAT", "NAMED", "WHATSAPP", "Athamma"),
    ("బ్యాంక్ భద్రతా హెచ్చరిక: డబ్బులు స్వీకరించడానికి ఎప్పుడూ UPI పిన్ ఎంటర్ చేయాల్సిన అవసరం లేదు. పిన్ కేవలం పంపడానికి మాత్రమే. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ఎస్‌బీఐ: మీ ఖాతాపై ₹5,00,000 పర్సనల్ లోన్ సిద్ధంగా ఉంది. ప్రాసెసింగ్ ఫీజు ₹1,000 వర్తిస్తుంది. దరఖాస్తు చేసుకోండి: https://sbi.co.in - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T")
]

TEST_SCAM_TE = [
    ("అత్యవసర ప్రకటన: విద్యుత్ బిల్లు చెల్లించనందున ఈ రాత్రి విద్యుత్ నిలిపివేత ఉంటుంది. సంప్రదించండి: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("సిబిఐ విచారణ: మీ పేరుతో మనీలాండరింగ్ అనుమానాలు ఉన్నాయి. కాల్‌లో ఉండండి మరియు పోలీసులకు చెప్పవద్దు.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("గృహప్రవేశం ఆహ్వాన పత్రిక చూడటానికి card_gruha.apk ఇన్‌స్టాల్ చేయండి.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ఆంధ్రా బ్యాంక్: ఖాతా రద్దు చేయబడుతుంది వెంటనే ధృవీకరించడానికి క్లిక్ చేయండి: http://andhrabank-kyc.buzz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ట్రాఫిక్ ఫైన్: జరిమానా కట్టండి echallan_online.apk ద్వారా. రిఫరెన్స్ ఐడి: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ఫోన్‌పే రివార్డ్: రూ. {amt} అందుకోవడానికి యూపీఐ పిన్ నమోదు చేయండి డబ్బు అందుకోవడానికి.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("సిమ్ కార్డు బ్లాక్ కాకుండా ఉండటానికి ఓటీపీ పంపండి త్వరగా.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("సర్వర్ సమస్య పరిష్కారం కోసం టీమ్‌వీవర్ ఇన్‌స్టాల్ చేయండి వెంటనే.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("రోజుకు రూ. 2000 సంపాదించండి ఇంట్లోనే. టెలిగ్రామ్ ఛానెల్ జాయిన్ అవ్వండి: http://t.me/earn_hyd", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ప్రధానమంత్రి ముద్ర లోన్: రుణం మంజూరైంది. ప్రాసెసింగ్ ఫీజు చెల్లించండి లింక్‌లో: http://mudra-loan-online.site", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("మీ డెలివరీ ఆగిపోయింది. చిరునామా అప్‌డేట్ చేయండి: http://courier-post-tg.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ఇబ్బందుల్లో ఉన్నాను డబ్బులు పంపండి, ఇది కొత్త నంబర్, అర్జెంట్‌గా పంపండి.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ఐసిఐసిఐ బ్యాంక్: కార్డ్ బ్లాక్ అయింది. కొనసాగించడానికి క్లిక్ చేయండి: http://icici-card-te.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("లక్కీ డ్రా విజేత! బహుమతి పొందడానికి ముందస్తు రుసుము ఇవ్వండి: http://reward-win-te.click", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("విద్యుత్ కనెక్షన్ రద్దు చేయబడుతుంది వెంటనే చెల్లించండి: http://power-tg-pay.link", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST_BENIGN_TE = [
    ("Payment of Rs {amt} received for TGSPDCL USC 10928374 on {date}. Official site: https://tgspdcl.com", "UTILITY", "DLT_HEADER", "SMS", "TG-TGSPDC-G"),
    ("Dear Customer, INR {amt}.00 debited from A/c XX5678 on {date}. Helpline: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 4,500.00 credited to A/c XX4321 via UPI. - HDFC Bank", "BANK", "DLT_HEADER", "SMS", "AX-HDFCBK-T"),
    ("654321 is your login OTP for Jio. Do not share this code with anyone. - Jio", "OTP", "DLT_HEADER", "SMS", "JM-JIOINF-P"),
    ("Your Airtel data pack is expiring soon. Recharge at https://airtel.in - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("బ్యాంక్ అధికారులు ఎప్పుడూ పిన్ లేదా పాస్‌వర్డ్ అడగరని గమనించండి. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ఉగాది పండుగ శుభాకాంక్షలు! ఈ నూతన సంవత్సరం మీ జీవితంలో సుఖశాంతులు తేవాలి.", "CHAT", "NAMED", "WHATSAPP", "Raghav"),
    ("ఈ వీకెండ్ వైజాగ్ బీచ్‌కు వెళ్దాం అనుకుంటున్నాము, నువ్వు వస్తావా?", "CHAT", "NAMED", "WHATSAPP", "Praveen"),
    ("వచ్చేటప్పుడు మార్కెట్ నుండి పాలు మరియు కూరగాయలు తీసుకురండి.", "CHAT", "NAMED", "WHATSAPP", "Amma"),
    ("రేపు సాయంత్రం ప్రసాద్స్ ఐమాక్స్‌లో కొత్త సినిమా చూద్దాం, టికెట్స్ బుక్ చేశాను.", "CHAT", "NAMED", "WHATSAPP", "Harish"),
    ("సెమిస్టర్ పరీక్షల టైమ్‌టేబుల్ వచ్చింది, వచ్చే నెల మొదలవుతాయి.", "CHAT", "NAMED", "WHATSAPP", "Bhavani"),
    ("నాన్నగారి ఆరోగ్యం ఇప్పుడు చాలా మెరుగుపడింది, ఆందోళన చెందవద్దు.", "CHAT", "NAMED", "WHATSAPP", "Akka"),
    ("రేపు ఉదయం 6 గంటలకు కేబీఆర్ పార్క్‌లో వాకింగ్‌కు వెళ్దాం.", "CHAT", "NAMED", "WHATSAPP", "Varun"),
    ("దసరా సెలవుల్లో ఊరికి వెళ్ళేందుకు బస్సు టికెట్లు దొరికాయి.", "CHAT", "NAMED", "WHATSAPP", "Annayya"),
    ("ఈరోజు ఇంట్లో పులిహోర మరియు బూరెలు చేశాము, సాయంత్రం రండి.", "CHAT", "NAMED", "WHATSAPP", "Pinni"),
    ("పొలంలో కొత్త పంట బాగా వచ్చింది, ఫోటోలు షేర్ చేశాను.", "CHAT", "NAMED", "WHATSAPP", "Ramesh"),
    ("Your Flipkart delivery is on the way. Track at https://flipkart.com", "DELIVERY", "DLT_HEADER", "SMS", "AD-FLPKRT-T"),
    ("ఇంటి ముందు పూల మొక్కలు చాలా అందంగా పూశాయి.", "CHAT", "NAMED", "WHATSAPP", "Geetha"),
    ("మధ్యాహ్నం లంచ్ సమయంలో ఆఫీస్ కేఫ్ వద్ద కలుద్దాం.", "CHAT", "NAMED", "WHATSAPP", "Santosh"),
    ("పాత ఫోటోలు చూస్తుంటే చిన్ననాటి జ్ఞాపకాలు గుర్తొచ్చాయి.", "CHAT", "NAMED", "WHATSAPP", "Murali")
]

# ==========================================
# 4. TAMIL (ta)
# ==========================================

DEV_SCAM_TA = [
    ("அன்புள்ள நுகர்வோரே, உங்கள் மின்சார கட்டணம் செலுத்தப்படாததால் இன்று இரவு 9:30 மணிக்கு மின் இணைப்பு துண்டிக்கப்படும். தொடர்பு கொள்ளவும்: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("சென்னை போலீஸ் கிரைம் பிராஞ்ச்: உங்கள் ஆதார் எண்ணில் போதைப்பொருள் பார்சல் பிடிபட்டது மற்றும் கைது வாரண்ட் பிறப்பிக்கப்பட்டுள்ளது. வீடியோ அழைப்பை துண்டிக்க வேண்டாம், யாரிடமும் சொல்ல வேண்டாம்.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("எங்கள் திருமண அழைப்பிதழை பார்க்க kalyana_pathirikai.apk பதிவிறக்கவும்.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("இந்தியன் வங்கி: 24 மணி நேரத்திற்குள் கணக்கு முடக்கப்படும். கேஒய்சி புதுப்பிக்க கிளிக் செய்யவும்: http://indianbank-kyc.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("போக்குவரத்து காவல்: நிலுவையில் உள்ள அபராதத்தை செலுத்த echallan_tn.apk நிறுவவும். எண்: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("வாழ்த்துகள்! ₹{amt} கேஷ்பேக் அங்கீகரிக்கப்பட்டது. பணம் பெற பின் உள்ளிடவும்.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("கணக்கை தொடர உங்கள் மொபைலுக்கு வந்த ஓடிபி சொல்லுங்கள்.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("மின்சார வாரியம்: தவறான பில் சரிசெய்ய அனிடெஸ்க் பதிவிறக்கவும்.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("வீட்டிலிருந்தே பகுதி நேர வேலை செய்து தினசரி ரூ 3000 சம்பாதிக்கவும். டெலிகிராம் குழுவில் இணையுங்கள்: http://t.me/earn_tamil", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("பிஎம் முத்ரா திட்டம்: குறைந்த வட்டியில் கடன் அங்கீகரிக்கப்பட்டது. பைல் சார்ஜ் அனுப்புங்கள்: http://mudra-loan-tn.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("இந்தியா போஸ்ட்: உங்கள் பார்சல் டெலிவரி தோல்வியடைந்தது. முகவரியை மாற்ற இணைப்பிற்கு செல்லவும்: http://indiapost-update-tn.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("இது எனது புதிய எண், மருத்துவமனையில் உள்ளேன் பணம் அனுப்புங்கள் அவசரமாக.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("எச்டிஎப்சி வங்கி: பான் கார்டு இணைக்கப்படாததால் கணக்கு முடக்கப்படும். கிளிக் செய்யவும்: http://hdfc-pan-tn.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("வாழ்த்துகள் நீங்கள் லாட்டரி வென்றுள்ளீர்கள் ரூ 10 லட்சம். பதிவு கட்டணம் செலுத்துங்கள்.", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("தமிழ்நாடு மின்சார வாரியம்: மின்சாரம் நிறுத்தப்படும் உடனே பில் செலுத்துங்கள்: http://tneb-bill-pay.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

DEV_BENIGN_TA = [
    ("Dear Consumer, TNEB power bill of Rs {amt} generated for Cons 0918273645. Pay at https://tnebltd.gov.in - TNEB", "UTILITY", "DLT_HEADER", "SMS", "TN-TNEBLT-G"),
    ("உங்கள் கணக்கு XX4321-லிருந்து INR {amt}.00 எடுக்கப்பட்டது {date}-ல். உதவி எண்: 18002583838 - Indian Bank", "BANK", "DLT_HEADER", "SMS", "IB-INDBNK-T"),
    ("Dear Customer, INR 35,000.00 credited to A/c XX9812 on {date}. Bal: INR 42,300.00. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("123456 என்பது உங்கள் ஓடிபி. இந்த எண்ணை யாரிடமும் பகிர வேண்டாம். - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("உங்கள் ஏர்டெல் ரீசார்ஜ் வெற்றிகரமாக முடிந்தது. செல்லுபடியாகும் காலம் 28 நாட்கள். நன்றி. - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("தமிழ்நாடு சைபர் கிரைம்: முன்பின் தெரியாத நபர்களுக்கு ஓடிபி அல்லது கடவுச்சொல்லை பகிராதீர்கள். விழிப்புடன் இருங்கள்.", "GOVERNMENT", "DLT_HEADER", "SMS", "TN-CHNPOL-G"),
    ("அனைவருக்கும் இனிய பொங்கல் நல்வாழ்த்துகள்! பொங்கல் திருநாள் உங்கள் வாழ்வில் மகிழ்ச்சியை கொண்டுவரட்டும்.", "CHAT", "NAMED", "WHATSAPP", "Karthikeyan"),
    ("இன்று மாலை அனைவரும் வெளியே சாப்பிட செல்வோம். 7 மணிக்கு தயாராக இருங்கள்.", "CHAT", "NAMED", "WHATSAPP", "Revathi"),
    ("மதுரை செல்வதற்கு ரயில் டிக்கெட் புக் ஆகிவிட்டது. காலை 6 மணி ரயில்.", "CHAT", "NAMED", "WHATSAPP", "Murugan"),
    ("நாளை ஆபிஸ் மீட்டிங் ஸ்லைடுகள் தயாராகிவிட்டன. ஒருமுறை சரிபார்த்துக் கொள்ளவும்.", "CHAT", "NAMED", "WHATSAPP", "Senthil"),
    ("கடையிலிருந்து வரும்போது காய்கறிகளும் பழங்களும் வாங்கி வாருங்கள்.", "CHAT", "NAMED", "WHATSAPP", "Amma"),
    ("மருத்துவர் சந்திப்பு நாளை மாலை 5 மணிக்கு உள்ளது, பழைய அறிக்கைகளை எடுத்து வாருங்கள்.", "CHAT", "NAMED", "WHATSAPP", "Appa"),
    ("தேர்வு முடிவுகள் இன்று மதியம் வெளியாகும். வாழ்த்துகள்!", "CHAT", "NAMED", "WHATSAPP", "Vignesh"),
    ("பிறந்தநாள் நல்வாழ்த்துகள் ஆனந்த்! இந்த ஆண்டு சிறப்பாக அமையட்டும்.", "CHAT", "NAMED", "WHATSAPP", "Saravanan"),
    ("மெரினா கடற்கரை காற்று மிகவும் இனிமையாக உள்ளது, புகைப்படம் அனுப்பியுள்ளேன்.", "CHAT", "NAMED", "WHATSAPP", "Deepak"),
    ("இன்று சென்னையில் பலத்த மழை பெய்கிறது, குடை எடுத்துச் செல்ல மறக்காதீர்கள்.", "CHAT", "NAMED", "WHATSAPP", "Priya"),
    ("Your Amazon package is out for delivery. Track at https://amazon.in", "DELIVERY", "DLT_HEADER", "SMS", "AD-AMAZON-T"),
    ("இன்றைய கிரிக்கெட் போட்டியில் இந்தியா மிகச் சிறப்பாக விளையாடியது!", "CHAT", "NAMED", "WHATSAPP", "Vijay"),
    ("கல்லூரி நண்பர்கள் சந்திப்பு அடுத்த ஞாயிற்றுக்கிழமை திட்டமிடப்பட்டுள்ளது. வருகிறாயா?", "CHAT", "NAMED", "WHATSAPP", "Manoj"),
    ("இன்று வீட்டில் சுவையான சாம்பார் சாதமும் பாயசமும் செய்துள்ளோம், சாப்பிட வாருங்கள்.", "CHAT", "NAMED", "WHATSAPP", "Chithi"),
    ("வங்கி பாதுகாப்பு அறிவிப்பு: பணம் பெறுவதற்கு ஒருபோதும் யுபிஐ பின்னை உள்ளிட தேவையில்லை. பின் பணம் அனுப்ப மட்டுமே. - Indian Bank", "BANK", "DLT_HEADER", "SMS", "IB-INDBNK-T"),
    ("இந்தியன் வங்கி: உங்களுக்கு ₹5,00,000 தனிநபர் கடன் வழங்கப்படுகிறது. செயலாக்கக் கட்டணம் ₹1,000 பொருந்தும். விண்ணப்பிக்கவும்: https://indianbank.in - Indian Bank", "BANK", "DLT_HEADER", "SMS", "IB-INDBNK-T")
]

TEST_SCAM_TA = [
    ("அவசர அறிவிப்பு: மின் கட்டணம் நிலுவையில் உள்ளதால் இன்று இரவு மின் இணைப்பு ரத்து செய்யப்படும். அழைக்கவும்: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("சிபிஐ விசாரணை: உங்கள் மீது சட்டவிரோத பணப்பரிவர்த்தனை வழக்கு பதிவு செய்யப்பட்டுள்ளது. அழைப்பில் இருங்கள் மற்றும் ரகசியமாக வைக்கவும்.", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("புதுமனை புகுவிழா அழைப்பிதழ் பார்க்க invitation_card.apk பதிவிறக்கவும்.", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("இந்தியன் ஓவர்சீஸ் வங்கி: கணக்கு இடைநிறுத்தப்படும் உடனே புதுப்பிக்க இணைப்பை கிளிக் செய்யவும்: http://iob-kyc-update.buzz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("டிராபிக் அபராதம்: நிலுவையை செலுத்த echallan_pay_tn.apk பதிவிறக்கவும். குறிப்பு: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("கூகுள் பே பரிசு: ரூ. {amt} பெற கியூஆர் ஸ்கேன் செய்து யுபிஐ பின் உள்ளிடவும் பணம் பெற.", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("சிம் கார்டு முடக்கப்படாமல் இருக்க உங்கள் எண்ணிற்கு வந்த ஓடிபி உடனடியாக அனுப்புங்கள்.", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("தொழில்நுட்ப கோளாறை சரிசெய்ய டீம்வியூவர் நிறுவுங்கள் இப்போதே.", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("தினசரி ரூ 2000 சம்பாதிக்கலாம் வீட்டில் இருந்தே. டெலிகிராம் சேனலில் இணையுங்கள்: http://t.me/earn_chennai", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("பிரதமர் முத்ரா கடன்: கடன் ஒப்புதல் வழங்கப்பட்டது. செயலாக்க கட்டணம் செலுத்துங்கள்: http://mudra-loan-online.top", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("உங்கள் பார்சல் நிறுத்தி வைக்கப்பட்டுள்ளது. முகவரியை சரிபார்க்க கிளிக் செய்யவும்: http://courier-post-tn.click", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ஆபத்தில் உள்ளேன் பணம் வேண்டும், இது புதிய எண், அவசரமாக பணம் அனுப்புங்கள்.", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ஐசிஐசிஐ வங்கி: அட்டை தடுக்கப்பட்டது. தொடர கிளிக் செய்யவும்: http://icici-card-ta.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("லக்கி டிரா வெற்றியாளர்! பரிசு வென்றுள்ளீர்கள். முன்பணம் கொடுங்கள் இந்த இணைப்பில்: http://lucky-gift-ta.click", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("மின் இணைப்பு துண்டிக்கப்படும் உடனே கட்டணம் செலுத்துங்கள்: http://power-tneb-pay.link", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST_BENIGN_TA = [
    ("Payment of Rs {amt} received for TNEB Consumer No 192837465 on {date}. Portal: https://tnebltd.gov.in", "UTILITY", "DLT_HEADER", "SMS", "TN-TNEBLT-G"),
    ("Dear Customer, INR {amt}.00 debited from A/c XX5678 on {date}. Balance: INR 21,450.00. - Indian Bank", "BANK", "DLT_HEADER", "SMS", "IB-INDBNK-T"),
    ("Dear Customer, INR 6,000.00 credited to A/c XX4321 via UPI. Helpline: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("654321 என்பது ஜியோ உள்நுழைவு ஓடிபி. இந்த குறியீட்டை யாருடனும் பகிர வேண்டாம். - Jio", "OTP", "DLT_HEADER", "SMS", "JM-JIOINF-P"),
    ("உங்கள் விஐ வேலிடிட்டி விரைவில் முடிகிறது. ரீசார்ஜ் செய்ய: https://myvi.in - Vi", "TELECOM", "DLT_HEADER", "SMS", "JD-VILNOT-P"),
    ("வங்கி அதிகாரிகள் எப்போதும் கடவுச்சொல் கேட்க மாட்டார்கள் என்பதை நினைவில் கொள்க. - RBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("இனிய தமிழ் புத்தாண்டு நல்வாழ்த்துகள்! இந்த புத்தாண்டு அனைவருக்கும் நலமும் வளமும் சேர்க்கட்டும்.", "CHAT", "NAMED", "WHATSAPP", "Balaji"),
    ("இந்த வார இறுதியில் ஊட்டி செல்ல திட்டமிட்டுள்ளோம், நீ வருகிறாயா?", "CHAT", "NAMED", "WHATSAPP", "Dinesh"),
    ("திரும்பி வரும்போது மளிகை சாமான்கள் வாங்கி வாருங்கள்.", "CHAT", "NAMED", "WHATSAPP", "Amma"),
    ("நாளை மாலை சத்யம் தியேட்டரில் புதிய படம் பார்க்க டிக்கெட் புக் செய்துவிட்டேன்.", "CHAT", "NAMED", "WHATSAPP", "Aravind"),
    ("பல்கலைக்கழக தேர்வு அட்டவணை வந்துவிட்டது, அடுத்த மாதம் தேர்வுகள் தொடங்கும்.", "CHAT", "NAMED", "WHATSAPP", "Divya"),
    ("அப்பாவின் உடல்நிலை இப்போது நலமாக உள்ளது, கவலைப்பட வேண்டாம்.", "CHAT", "NAMED", "WHATSAPP", "Akka"),
    ("நாளை காலை 6 மணிக்கு பெசன்ட் நகர் கடற்கரையில் நடைப்பயிற்சி செல்வோம்.", "CHAT", "NAMED", "WHATSAPP", "Prakash"),
    ("தீபாவளி பண்டிகைக்கு சொந்த ஊர் செல்ல பேருந்து டிக்கெட் கிடைத்துவிட்டது.", "CHAT", "NAMED", "WHATSAPP", "Annan"),
    ("இன்று வீட்டில் முறுக்கும் அதிரசமும் செய்துள்ளோம், மாலையில் வாருங்கள்.", "CHAT", "NAMED", "WHATSAPP", "Periyamma"),
    ("தோட்டத்தில் ரோஜா செடிகள் நன்றாக வளர்ந்து பூக்கள் பூத்துள்ளன.", "CHAT", "NAMED", "WHATSAPP", "Kavitha"),
    ("Your Flipkart order has been delivered. Track at https://flipkart.com", "DELIVERY", "DLT_HEADER", "SMS", "AD-FLPKRT-T"),
    ("விருந்தினர்கள் அனைவரும் வந்துவிட்டனர், விசேஷம் சிறப்பாக நடக்கிறது.", "CHAT", "NAMED", "WHATSAPP", "Sudhakar"),
    ("மதிய உணவு நேரத்தில் அலுவலக உணவகத்தில் சந்திப்போம்.", "CHAT", "NAMED", "WHATSAPP", "Ganesh"),
    ("பழைய பள்ளி புகைப்படங்களை பார்த்தபோது மகிழ்ச்சியாக இருந்தது.", "CHAT", "NAMED", "WHATSAPP", "Ramesh")
]

# ==========================================
# 5. ODIA (or)
# ==========================================

DEV_SCAM_OR = [
    ("ପ୍ରିୟ ଗ୍ରାହକ, ଆପଣଙ୍କ ବିଜୁଳି ବିଲ୍ ବାକି ଥିବାରୁ ଆଜି ରାତି ୯:୩୦ ରେ ବିଦ୍ୟୁତ୍ ସଂଯୋଗ ବିଚ୍ଛିନ୍ନ ହେବ। ବିଦ୍ୟୁତ୍ ଅଧିକାରୀଙ୍କ ସହ ଯୋଗାଯୋଗ କରନ୍ତୁ: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ଓଡ଼ିଶା ପୋଲିସ କ୍ରାଇମ ବ୍ରାଞ୍ଚ: ଆପଣଙ୍କ ଆଧାର ନମ୍ବରରେ ନିଶାଦ୍ରବ୍ୟ ପାର୍ସଲ ଧରାପଡିଛି ଏବଂ ଗିରଫ ପରୱାନା ଜାରି ହୋଇଛି। ଭିଡିଓ କଲ୍ କାଟନ୍ତୁ ନାହିଁ, କାହାକୁ କୁହନ୍ତୁ ନାହିଁ।", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ଆମ ବାହାଘର ନିମନ୍ତ୍ରଣ ପତ୍ର ଦେଖିବା ପାଇଁ wedding_card.apk ଡାଉନଲୋଡ୍ କରନ୍ତୁ।", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ଏସବିଆଇ ଗ୍ରାହକ: ୨୪ ଘଣ୍ଟା ମଧ୍ୟରେ ଖାତା ବନ୍ଦ ହୋଇଯିବ। କେୱାଇସି ଅପଡେଟ୍ କରନ୍ତୁ: http://sbi-kyc-odia.top", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ଟ୍ରାଫିକ୍ ଚାଲାଣ: ବାକି ଥିବା ଚାଲାଣ ଦାଖଲ ପାଇଁ echallan_od.apk ଇନଷ୍ଟଲ୍ କରନ୍ତୁ। ନମ୍ବର: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ଅଭିନନ୍ଦନ! ₹{amt} କ୍ୟାସବ୍ୟାକ୍ ମଞ୍ଜୁର ହୋଇଛି। ଟଙ୍କା ପାଇବା ପାଇଁ ପିନ୍ ଦିଅନ୍ତୁ।", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ଖାତା ଚାଲୁ ରଖିବା ପାଇଁ ଆପଣଙ୍କ ମୋବାଇଲକୁ ଆସିଥିବା ଓଟିପି ଦିଅନ୍ତୁ।", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ବିଦ୍ୟୁତ୍ ବିଭାଗ: ବିଲ୍ ସଂଶୋଧନ ପାଇଁ ଆନିଡେସ୍କ ଡାଉନଲୋଡ୍ କରନ୍ତୁ।", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ଘରେ ବସି ପାର୍ଟ ଟାଇମ୍ କାମ କରି ଦୈନିକ ୩୦୦୦ ଟଙ୍କା ରୋଜଗାର କରନ୍ତୁ। ଟେଲିଗ୍ରାମ୍ ଗ୍ରୁପ୍‌ରେ ଯୋଗ ଦିଅନ୍ତୁ: http://t.me/earn_odia", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ପିଏମ ମୁଦ୍ରା ଯୋଜନା: ସୁବିଧା କିସ୍ତିରେ ଋଣ ମଞ୍ଜୁର ହୋଇଛି। ଫାଇଲ୍ ଚାର୍ଜ ପଠାନ୍ତୁ: http://mudra-loan-od.xyz", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ଇଣ୍ଡିଆ ପୋଷ୍ଟ: ଆପଣଙ୍କ ପାର୍ସଲ ବିତରଣ ବିଫଳ ହୋଇଛି। ଠିକଣା ପରିବର୍ତ୍ତନ ପାଇଁ ଲିଙ୍କ୍ ଯାଆନ୍ତୁ: http://indiapost-update-od.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ଏହା ମୋର ନୂଆ ନମ୍ବର, ଡାକ୍ତରଖାନାରେ ଅଛି ଟଙ୍କା ପଠାନ୍ତୁ ଜରୁରୀ।", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ଏଚଡିଏଫସି ବ୍ୟାଙ୍କ: ପାନ୍ ଲିଙ୍କ୍ ନଥିବାରୁ ଖାତା ସ୍ଥଗିତ ହେବ। କ୍ଲିକ୍ କରନ୍ତୁ: http://hdfc-pan-od.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ଅଭିନନ୍ଦନ ଆପଣ ଲଟେରୀ ଜିତିଛନ୍ତି ୧୦ ଲକ୍ଷ ଟଙ୍କା। ପଞ୍ଜୀକରଣ ଫି ଜମା କରନ୍ତୁ।", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ଓଡ଼ିଶା ବିଦ୍ୟୁତ: ବିଦ୍ୟୁତ୍ କାଟି ଦିଆଯିବ ତୁରନ୍ତ ବିଲ୍ ଦିଅନ୍ତୁ: http://tpcodl-pay.top", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

DEV_BENIGN_OR = [
    ("Dear Consumer, TPCODL electricity bill of Rs {amt} generated for Consumer No 081928374. Pay at https://tpcentralodisha.com - TPCODL", "UTILITY", "DLT_HEADER", "SMS", "OD-TPCODL-G"),
    ("ଆପଣଙ୍କ ଖାତା XX4321 ରୁ INR {amt}.00 ଡେବିଟ୍ ହୋଇଛି {date} ରେ। ହେଲ୍ପଲାଇନ୍: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 30,000.00 credited to A/c XX9812 on {date}. Bal: INR 38,100.00. - PNB", "BANK", "DLT_HEADER", "SMS", "VM-PNBBNK-T"),
    ("123456 ଆପଣଙ୍କ ଓଟିପି ଅଟେ। ଏହି ଓଟିପି କାହା ସହିତ ସେୟାର କରନ୍ତୁ ନାହିଁ। - SBI", "OTP", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ଆପଣଙ୍କ ଏୟାରଟେଲ୍ ରିଚାର୍ଜ ସଫଳ ହୋଇଛି। ବୈଧତା ୨୮ ଦିନ। ଧନ୍ୟବାଦ। - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("ଓଡ଼ିଶା ପୋଲିସ: ଅଜଣା ବ୍ୟକ୍ତିଙ୍କ ସହିତ ଓଟିପି କିମ୍ବା ବ୍ୟାଙ୍କ ପାସୱାର୍ଡ ସେୟାର କରନ୍ତୁ ନାହିଁ। ସତର୍କ ରୁହନ୍ତୁ।", "GOVERNMENT", "DLT_HEADER", "SMS", "OD-ODPOLC-G"),
    ("ପବିତ୍ର ରଜ ପର୍ବର ହାର୍ଦ୍ଦିକ ଶୁଭେଚ୍ଛା ଓ ଶୁଭକାମନା! ପରିବାର ସହ ଆନନ୍ଦରେ ରୁହନ୍ତୁ।", "CHAT", "NAMED", "WHATSAPP", "Biswajit"),
    ("ଆଜି ସନ୍ଧ୍ୟାରେ ସମସ୍ତେ ବାହାରକୁ ଖାଇବାକୁ ଯିବା। ୭ଟାରେ ପ୍ରସ୍ତୁତ ରହିବ।", "CHAT", "NAMED", "WHATSAPP", "Lopamudra"),
    ("ପୁରୀ ଜଗନ୍ନାଥ ଦର୍ଶନ ପାଇଁ ଟ୍ରେନ୍ ଟିକେଟ୍ ବୁକ୍ ହୋଇଗଲା। ସକାଳ ୬ଟା ଟ୍ରେନ୍।", "CHAT", "NAMED", "WHATSAPP", "Manas"),
    ("ଆସନ୍ତାକାଲି ଅଫିସ୍ ମିଟିଂ ପ୍ରେଜେଣ୍ଟେସନ୍ ସରିଛି। ଥରେ ଦେଖିନିଅନ୍ତୁ।", "CHAT", "NAMED", "WHATSAPP", "Subrat"),
    ("ବଜାରରୁ ଫେରିବା ବେଳେ ପନିପରିବା ଓ ଫଳ ନେଇ ଆସିବ।", "CHAT", "NAMED", "WHATSAPP", "Bou"),
    ("ଡାକ୍ତରଙ୍କ ଆପଏଣ୍ଟମେଣ୍ଟ ଆସନ୍ତାକାଲି ଅପରାହ୍ନ ୫ଟାରେ ଅଛି, ରିପୋର୍ଟ ସାଙ୍ଗରେ ନେବ।", "CHAT", "NAMED", "WHATSAPP", "Bapa"),
    ("ପରୀକ୍ଷା ଫଳାଫଳ ଆଜି ପ୍ରକାଶ ପାଇବ। ବହୁତ ବହୁତ ଶୁଭେଚ୍ଛା!", "CHAT", "NAMED", "WHATSAPP", "Satyajit"),
    ("ଜନ୍ମଦିନର ଅନେକ ଅନେକ ଶୁଭେଚ୍ଛା ସୌମ୍ୟ! ଆଗାମୀ ବର୍ଷ ଭଲରେ କଟୁ।", "CHAT", "NAMED", "WHATSAPP", "Ashish"),
    ("କଟକର ଦହିବରା ଆଳୁଦମ ବହୁତ ସ୍ୱାଦିଷ୍ଟ ଥିଲା, ଫଟୋ ପଠାଇଲି।", "CHAT", "NAMED", "WHATSAPP", "Priyabrata"),
    ("ଆଜି ଭୁବନେଶ୍ୱରରେ ପ୍ରବଳ ବର୍ଷା ହେଉଛି, ଛତା ସାଙ୍ଗରେ ନେବାକୁ ଭୁଲିବ ନାହିଁ।", "CHAT", "NAMED", "WHATSAPP", "Smruti"),
    ("Your Amazon delivery is arriving today. Track at https://amazon.in", "DELIVERY", "DLT_HEADER", "SMS", "AD-AMAZON-T"),
    ("ଆଜି ମ୍ୟାଚ୍‌ରେ ଭାରତ ବହୁତ ଭଲ ଖେଳିଲା!", "CHAT", "NAMED", "WHATSAPP", "Debashis"),
    ("କଲେଜ ସାଙ୍ଗମାନଙ୍କ ମିଳନ ଆସନ୍ତା ରବିବାର ହେବ। ତୁମେ ଆସୁଛ ତ?", "CHAT", "NAMED", "WHATSAPP", "Ranjan"),
    ("ଆଜି ଘରେ ଛେନାପୋଡ଼ ଓ ଖିରି ତିଆରି ହୋଇଛି, ସନ୍ଧ୍ୟାରେ ଖାଇବାକୁ ଆସ।", "CHAT", "NAMED", "WHATSAPP", "Khuda"),
    ("ବ୍ୟାଙ୍କ ସୁରକ୍ଷା ସୂଚନା: ଟଙ୍କା ପାଇବା ପାଇଁ କେବେ ବି UPI PIN ଦେବାର ଆବଶ୍ୟକତା ନାହିଁ। ପିନ୍ କେବଳ ଟଙ୍କା ପଠାଇବା ପାଇଁ। - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ଏସବିଆଇ: ଆପଣଙ୍କ ପାଇଁ ₹୫,୦୦,୦୦୦ ବ୍ୟକ୍ତିଗତ ଋଣ ଉପଲବ୍ଧ। ପ୍ରୋସେସିଂ ଫି ₹୧,୦୦୦। ଆବେଦନ କରନ୍ତୁ: https://sbi.co.in - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T")
]

TEST_SCAM_OR = [
    ("ଜରୁରୀ ସୂଚନା: ବିଜୁଳି ବିଲ୍ ବାକି ଥିବାରୁ ଆଜି ରାତିରେ ବିଜୁଳି ବନ୍ଦ ହେବ। ଯୋଗାଯୋଗ: +919876543210", "UTILITY_DISCONNECT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ସିବିଆଇ ତଦନ୍ତ: ଆପଣଙ୍କ ନାମରେ ବେଆଇନ କାରବାର ମାମଲା ଦାୟର ହୋଇଛି। କଲ୍ କାଟନ୍ତୁ ନାହିଁ ଓ କଲ୍‌ରେ ରୁହନ୍ତୁ।", "AUTHORITY_DIGITAL_ARREST", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ଗୃହପ୍ରବେଶ ନିମନ୍ତ୍ରଣ ପତ୍ର ଦେଖିବାକୁ card_gruhapravesh.apk ଡାଉନଲୋଡ୍ କରନ୍ତୁ।", "MALICIOUS_APK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ୟୁକୋ ବ୍ୟାଙ୍କ: ପାନ୍ କାର୍ଡ ଲିଙ୍କ୍ ନଥିବାରୁ ଖାତା ବନ୍ଦ ହେବ। ଅପଡେଟ୍ କରନ୍ତୁ: http://ucobank-kyc.buzz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ଟ୍ରାଫିକ୍ ଫାଇନ୍: ଜୋରିମାନା ଭରନ୍ତୁ echallan_odisha.apk ଡାଉନଲୋଡ୍ କରି। ରେଫରେନ୍ସ: {ref}", "MALICIOUS_APK", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ଫୋନପେ ପୁରସ୍କାର: ଟଙ୍କା {amt} ପାଇବା ପାଇଁ କ୍ୟୁଆର୍ ସ୍କାନ୍ କରି ୟୁପିଆଇ ପିନ୍ ପ୍ରବେଶ କରନ୍ତୁ ଟଙ୍କା ଗ୍ରହଣ ପାଇଁ।", "UPI_PAYMENT_FRAUD", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ସିମ୍ କାର୍ଡ ଚାଲୁ ରଖିବାକୁ ଓଟିପି କୋଡ୍ ଦିଅନ୍ତୁ ଏବେ ହିଁ।", "OTP_ACCOUNT_TAKEOVER", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ସର୍ଭର ସମସ୍ୟା ସମାଧାନ ପାଇଁ ଟିମ୍‌ଭ୍ୟୁଅର୍ ଇନଷ୍ଟଲ୍ କରନ୍ତୁ ତୁରନ୍ତ।", "REMOTE_ACCESS", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ଦୈନିକ ୨୦୦୦ ଟଙ୍କା ରୋଜଗାର କରନ୍ତୁ ଘରେ ବସି। ଟେଲିଗ୍ରାମ୍ ଚ୍ୟାନେଲ୍ ଜଏନ୍ କରନ୍ତୁ: http://t.me/earn_bbsr", "JOB_TASK", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ପ୍ରଧାନମନ୍ତ୍ରୀ ମୁଦ୍ରା ଯୋଜନା: ଋଣ ସ୍ୱୀକୃତ ହୋଇଛି। ପ୍ରୋସେସିଂ ଫି ଦିଅନ୍ତୁ ଏହି ଲିଙ୍କରେ: http://mudra-scheme-od.site", "LOAN_CREDIT", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ଆପଣଙ୍କ କୁରିୟର ପହଞ୍ଚିପାରିଲା ନାହିଁ। ଠିକଣା ଅପଡେଟ୍ କରନ୍ତୁ: http://courier-post-od.top", "DELIVERY_COURIER", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ଅସୁବିଧାରେ ଅଛି ଟଙ୍କା ପଠାନ୍ତୁ, ଏହା ନୂଆ ନମ୍ବର ଅଟେ, ତୁରନ୍ତ ପଠାନ୍ତୁ।", "IMPERSONATED_CONTACT", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ଆଇସିଆଇସିଆଇ ବ୍ୟାଙ୍କ: କାର୍ଡ ବ୍ଲକ୍ ହୋଇଛି। ଚାଲୁ କରିବାକୁ କ୍ଲିକ୍ କରନ୍ତୁ: http://icici-card-or.xyz", "PHISHING_BANK_KYC", "NUMBER_ONLY", "SMS", "+919876543210"),
    ("ଲକି ଡ୍ର' ବିଜେତା! ପୁରସ୍କାର ଜିତିଛନ୍ତି। ଅଗ୍ରିମ ରାଶି ଦିଅନ୍ତୁ: http://prize-win-or.click", "LOTTERY_PRIZE", "NUMBER_ONLY", "WHATSAPP", "+919876543210"),
    ("ବିଦ୍ୟୁତ୍ ବିଭାଗ: ବିଦ୍ୟୁତ୍ ସଂଯୋଗ ବାତିଲ ହେବ ତୁରନ୍ତ ବିଲ୍ ଦିଅନ୍ତୁ: http://power-od-pay.link", "UTILITY_DISCONNECT", "NUMBER_ONLY", "SMS", "+919876543210")
]

TEST_BENIGN_OR = [
    ("Payment of Rs {amt} received for TPCODL Cons No 102938475 on {date}. Portal: https://tpcentralodisha.com", "UTILITY", "DLT_HEADER", "SMS", "OD-TPCODL-G"),
    ("Dear Customer, INR {amt}.00 debited from A/c XX5678 on {date}. Balance: INR 16,890.00. - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("Dear Customer, INR 5,000.00 credited to A/c XX4321 via UPI. Helpline: 18002583838 - SBI", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("654321 is your login OTP for Jio. Do not share this code with anyone. - Jio", "OTP", "DLT_HEADER", "SMS", "JM-JIOINF-P"),
    ("Your Airtel pack is expiring soon. Recharge at https://airtel.in - Airtel", "TELECOM", "DLT_HEADER", "SMS", "JD-AIRTEL-P"),
    ("ବ୍ୟାଙ୍କ କର୍ମଚାରୀ କେବେ ବି ଓଟିପି କିମ୍ବା ପାସୱାର୍ଡ ମାଗନ୍ତି ନାହିଁ। - RBI Kehta Hai", "BANK", "DLT_HEADER", "SMS", "VM-SBIBNK-T"),
    ("ପବିତ୍ର ରଥଯାତ୍ରାର ହାର୍ଦ୍ଦିକ ଶୁଭେଚ୍ଛା! ପ୍ରଭୁ ଜଗନ୍ନାଥ ଆପଣଙ୍କ ମଙ୍ଗଳ କରନ୍ତୁ।", "CHAT", "NAMED", "WHATSAPP", "Jagannath"),
    ("ଏହି ଶନିବାର ଚିଲିକା ବୁଲିବାକୁ ଯିବା କଥା ଭାବୁଛୁ, ତୁମେ ଯିବ କି?", "CHAT", "NAMED", "WHATSAPP", "Siddharth"),
    ("ଆସିଲା ବେଳେ ଦୋକାନରୁ ଚାଉଳ ଓ ଡାଲି ନେଇ ଆସିବ।", "CHAT", "NAMED", "WHATSAPP", "Bou"),
    ("କାଲି ସନ୍ଧ୍ୟାରେ ରବୀନ୍ଦ୍ର ମଣ୍ଡପରେ ନାଟକ ଦେଖିବାକୁ ଟିକେଟ୍ ବୁକ୍ କରିଛି।", "CHAT", "NAMED", "WHATSAPP", "Swadhin"),
    ("ସେମିଷ୍ଟାର୍ ପରୀକ୍ଷା ତାରିଖ ପ୍ରକାଶ ପାଇଛି, ଆସନ୍ତା ମାସରେ ଆରମ୍ଭ।", "CHAT", "NAMED", "WHATSAPP", "Lipika"),
    ("ବାପାଙ୍କ ଦେହ ଏବେ ବହୁତ ଭଲ ଅଛି, ବ୍ୟସ୍ତ ହୁଅ ନାହିଁ।", "CHAT", "NAMED", "WHATSAPP", "Nani"),
    ("କାଲି ସକାଳ ୬ଟାରେ ଏକାମ୍ର କାନନରେ ପ୍ରାତଃ ଭ୍ରମଣ ପାଇଁ ଯିବା।", "CHAT", "NAMED", "WHATSAPP", "Pradeep"),
    ("ଦଶହରା ଛୁଟିରେ ଗାଁକୁ ଯିବା ପାଇଁ ବସ୍ ଟିକେଟ୍ ମିଳିଗଲା।", "CHAT", "NAMED", "WHATSAPP", "Bhai"),
    ("ଆଜି ଘରେ ଚକୁଳି ପିଠା ଓ ଡାଲମା ବନେଇଛୁ, ସନ୍ଧ୍ୟାରେ ଆସିବ।", "CHAT", "NAMED", "WHATSAPP", "Mausi"),
    ("ବଗିଚାରେ ନୂଆ ଫୁଲ ଗଛ ଲଗାଇଲି, ବହୁତ ସୁନ୍ଦର ଫୁଲ ଫୁଟିଛି।", "CHAT", "NAMED", "WHATSAPP", "Rasmita"),
    ("Your Flipkart order has arrived at the hub. Track at https://flipkart.com", "DELIVERY", "DLT_HEADER", "SMS", "AD-FLPKRT-T"),
    ("ସବୁ ସମ୍ପର୍କୀୟ ପହଞ୍ଚିଗଲେଣି, ଉତ୍ସବ ବହୁତ ଭଲରେ ଚାଲିଛି।", "CHAT", "NAMED", "WHATSAPP", "Tapas"),
    ("ଖରାବେଳେ ଅଫିସ୍ କ୍ୟାଣ୍ଟିନ୍‌ରେ ଭେଟିବା।", "CHAT", "NAMED", "WHATSAPP", "Deepak"),
    ("ପୁରୁଣା ଦିନ କଥା ମନେ ପଡିଗଲା ପିଲାଦିନ ଫଟୋ ଦେଖି।", "CHAT", "NAMED", "WHATSAPP", "Subhashree")
]


def generate_all():
    repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    eval_dir = os.path.join(repo_root, "eval")
    os.makedirs(eval_dir, exist_ok=True)

    configs = [
        ("bn", DEV_SCAM_BN, DEV_BENIGN_BN, TEST_SCAM_BN, TEST_BENIGN_BN),
        ("mr", DEV_SCAM_MR, DEV_BENIGN_MR, TEST_SCAM_MR, TEST_BENIGN_MR),
        ("te", DEV_SCAM_TE, DEV_BENIGN_TE, TEST_SCAM_TE, TEST_BENIGN_TE),
        ("ta", DEV_SCAM_TA, DEV_BENIGN_TA, TEST_SCAM_TA, TEST_BENIGN_TA),
        ("or", DEV_SCAM_OR, DEV_BENIGN_OR, TEST_SCAM_OR, TEST_BENIGN_OR)
    ]

    summary = {}

    for lang, dev_scams, dev_benign, test_scams, test_benign in configs:
        # Generate dev: 60 scams (from 15 templates) + 100 benign (from 20 templates) = 160 rows (62.5% benign)
        dev_scam_rows = make_rows(dev_scams, "scam", lang, 60, f"dev-{lang}-scam", f"dev-{lang}-scam")
        dev_ben_rows = make_rows(dev_benign, "benign", lang, 100, f"dev-{lang}-ben", f"dev-{lang}-ben")
        dev_rows = dev_scam_rows + dev_ben_rows
        random.shuffle(dev_rows)

        # Generate test: 60 scams (from 15 templates) + 100 benign (from 20 templates) = 160 rows (62.5% benign)
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
