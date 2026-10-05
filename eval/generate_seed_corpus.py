import json
import random

random.seed(42)

scam_templates = [
    # MALICIOUS_APK
    ("Dear customer, please download wedding_invitation.pdf.apk to see wedding details", "MALICIOUS_APK", "en", "NUMBER_ONLY"),
    ("Your vehicle challan pending. Download echallan_pay.apk immediately", "MALICIOUS_APK", "en", "NUMBER_ONLY"),
    ("Install update.apk from http://sbi-app.xyz/update to prevent account block", "MALICIOUS_APK", "en", "NUMBER_ONLY"),
    ("Delivery boy could not deliver parcel. Install indiapost.apk to update address: http://indiapost-update.xyz/app.apk", "MALICIOUS_APK", "en", "NUMBER_ONLY"),
    ("शादी का कार्ड देखने के लिए shadi_card.pdf.apk डाउनलोड करें", "MALICIOUS_APK", "hi", "NUMBER_ONLY"),
    ("ट्रैफिक चालान लंबित है। challan_pay.apk तुरंत डाउनलोड करें", "MALICIOUS_APK", "hi", "NUMBER_ONLY"),
    ("Shadi ka card dekhne ke liye wedding_card.pdf.apk install karein", "MALICIOUS_APK", "hi-Latn", "NUMBER_ONLY"),
    ("Aapka parcel delivery fail ho gaya. indiapost_service.apk install karo", "MALICIOUS_APK", "hi-Latn", "NUMBER_ONLY"),

    # PHISHING_BANK_KYC
    ("Dear SBI user, your account will be blocked within 24 hours. Update KYC at http://sbi-kyc-verify.xyz immediately", "PHISHING_BANK_KYC", "en", "NUMBER_ONLY"),
    ("HDFC Alert: Your netbanking is suspended due to PAN card expiry. Click link to verify: http://hdfc-pan-update.top", "PHISHING_BANK_KYC", "en", "NUMBER_ONLY"),
    ("ICICI Bank: Update your KYC immediately to avoid card suspension: http://icici-verify.click", "PHISHING_BANK_KYC", "en", "NUMBER_ONLY"),
    ("एसबीआई खाताधारक: 24 घंटे के अंदर खाता बंद हो जाएगा। तुरंत अपडेट करें: http://sbi-kyc.xyz", "PHISHING_BANK_KYC", "hi", "NUMBER_ONLY"),
    ("एचडीएफसी बैंक: पैन कार्ड अपडेट न होने के कारण खाता निलंबित। http://hdfc-kyc.site पर क्लिक करें", "PHISHING_BANK_KYC", "hi", "NUMBER_ONLY"),
    ("Dear customer, aapka account block ho gaya hai. Turant link par click karke update karo: http://sbi-online.buzz", "PHISHING_BANK_KYC", "hi-Latn", "NUMBER_ONLY"),
    ("SBI alert: kyc expire ho gayi hai, 24 ghante me band ho jayega: http://sbi-kyc.top", "PHISHING_BANK_KYC", "hi-Latn", "NUMBER_ONLY"),

    # AUTHORITY_DIGITAL_ARREST
    ("This is CBI officer. Digital arrest warrant issued for narcotics parcel. Stay on video call, do not tell anyone.", "AUTHORITY_DIGITAL_ARREST", "en", "NUMBER_ONLY"),
    ("Mumbai Police Crime Branch: FIR registered against your Aadhaar for money laundering. Do not disconnect call, pay penalty immediately.", "AUTHORITY_DIGITAL_ARREST", "en", "NUMBER_ONLY"),
    ("Customs Department: Illegal package with drugs seized in your name. Arrest warrant issued. Contact officer immediately.", "AUTHORITY_DIGITAL_ARREST", "en", "NUMBER_ONLY"),
    ("सीबीआई अधिकारी: आपके खिलाफ डिजिटल अरेस्ट वारंट जारी हुआ है। वीडियो कॉल पर बने रहें, किसी को मत बताना।", "AUTHORITY_DIGITAL_ARREST", "hi", "NUMBER_ONLY"),
    ("पुलिस मुख्यालय: आपके आधार पर मनी लॉन्ड्रिंग केस दर्ज हुआ है। तुरंत जुर्माना भरें।", "AUTHORITY_DIGITAL_ARREST", "hi", "NUMBER_ONLY"),
    ("CBI Crime Branch: Aapke naam par digital arrest warrant nikla hai. Call mat katna, kisi ko mat batana.", "AUTHORITY_DIGITAL_ARREST", "hi-Latn", "NUMBER_ONLY"),
    ("Police notice: narcotics parcel pakda gaya hai aapke aadhaar par. Turant penalty transfer karo.", "AUTHORITY_DIGITAL_ARREST", "hi-Latn", "NUMBER_ONLY"),

    # UTILITY_DISCONNECT
    ("Dear consumer, your electricity will be disconnected tonight 9:30 pm due to unpaid bill. Call officer immediately or click: http://bijli-bill.xyz", "UTILITY_DISCONNECT", "en", "NUMBER_ONLY"),
    ("Power Corporation notice: Electricity power cutoff tonight 9:30 pm. Pay your bill now: http://power-update.click", "UTILITY_DISCONNECT", "en", "NUMBER_ONLY"),
    ("प्रिय उपभोक्ता, आज रात 9:30 बजे बिजली कनेक्शन काट दिया जाएगा। तुरंत बिल भरें: http://bijli-pay.xyz", "UTILITY_DISCONNECT", "hi", "NUMBER_ONLY"),
    ("बिजली विभाग: बिल जमा न होने के कारण आज रात लाइट कट जाएगी। संपर्क करें।", "UTILITY_DISCONNECT", "hi", "NUMBER_ONLY"),
    ("Dear customer, aapki bijli kat jayegi aaj raat 9:30 baje. Turant payment karein: http://light-bill.top", "UTILITY_DISCONNECT", "hi-Latn", "NUMBER_ONLY"),
    ("Bijli connection kaat diya jayega aaj raat. Turant bill bharo.", "UTILITY_DISCONNECT", "hi-Latn", "NUMBER_ONLY"),

    # UPI_PAYMENT_FRAUD
    ("Congratulations! Rs 5000 cashback approved. Enter UPI PIN to receive money in your bank account immediately.", "UPI_PAYMENT_FRAUD", "en", "NUMBER_ONLY"),
    ("PhonePe Reward: Scan this QR code and enter your PIN to accept Rs. 3,500 in your account.", "UPI_PAYMENT_FRAUD", "en", "NUMBER_ONLY"),
    ("बधाई हो! ₹5000 कैशबैक स्वीकृत हुआ। पैसे पाने के लिए यूपीआई पिन दर्ज करें।", "UPI_PAYMENT_FRAUD", "hi", "NUMBER_ONLY"),
    ("रिफंड अप्रूव हुआ है ₹2000 का। पैसे लेने के लिए अपना यूपीआई पिन डालें।", "UPI_PAYMENT_FRAUD", "hi", "NUMBER_ONLY"),
    ("Rs 4500 cashback approved! Paise lene ke liye upi pin enter karo abhi ke abhi.", "UPI_PAYMENT_FRAUD", "hi-Latn", "NUMBER_ONLY"),
    ("PhonePe refund mila hai 2500 ka. Qr scan karke pin dalo paise account me aa jayenge.", "UPI_PAYMENT_FRAUD", "hi-Latn", "NUMBER_ONLY"),

    # OTP_ACCOUNT_TAKEOVER
    ("Dear customer, please share your OTP to prevent SIM card blocked within 24 hours.", "OTP_ACCOUNT_TAKEOVER", "en", "NUMBER_ONLY"),
    ("Bank verification: tell me the OTP received on your mobile to complete KYC update.", "OTP_ACCOUNT_TAKEOVER", "en", "NUMBER_ONLY"),
    ("कृपया अपना ओटीपी बताएं ताकि आपका बैंक खाता बंद होने से बच सके।", "OTP_ACCOUNT_TAKEOVER", "hi", "NUMBER_ONLY"),
    ("Sim card band hone se bachane ke liye otp batao turant.", "OTP_ACCOUNT_TAKEOVER", "hi-Latn", "NUMBER_ONLY"),
    ("Bank officer here. Khata chalu rakhne ke liye otp share karo.", "OTP_ACCOUNT_TAKEOVER", "hi-Latn", "NUMBER_ONLY"),

    # REMOTE_ACCESS
    ("Bank Support: Install AnyDesk app immediately to resolve debit card blocking issue.", "REMOTE_ACCESS", "en", "NUMBER_ONLY"),
    ("Electricity Board: Download TeamViewer quicksupport to rectify wrong billing amount.", "REMOTE_ACCESS", "en", "NUMBER_ONLY"),
    ("बैंक सहायता: एनीडेस्क ऐप इंस्टॉल करें ताकि आपका खाता अनब्लॉक किया जा सके।", "REMOTE_ACCESS", "hi", "NUMBER_ONLY"),
    ("Bank KYC fix karne ke liye anydesk install karo turant.", "REMOTE_ACCESS", "hi-Latn", "NUMBER_ONLY"),

    # JOB_TASK
    ("Work from home: Earn 3000 daily by liking YouTube videos. Message me on Telegram: https://t.me/job_tasks", "JOB_TASK", "en", "NUMBER_ONLY"),
    ("Part time online job: daily payout Rs 2500 for rating hotels. Contact on Telegram: https://t.me/earn_daily", "JOB_TASK", "en", "NUMBER_ONLY"),
    ("घर बैठे कमाए रोजाना ₹3000, आसान काम। टेलीग्राम पर संपर्क करें: https://t.me/work_from_home", "JOB_TASK", "hi", "NUMBER_ONLY"),
    ("Part time job: daily 3000 kamaye youtube video like karke. Telegram join karo: https://t.me/daily_task", "JOB_TASK", "hi-Latn", "NUMBER_ONLY"),

    # INVESTMENT_TRADING
    ("Guaranteed returns: Double your investment in 7 days! Join VIP stock tips group on Telegram: https://t.me/crypto_vip", "INVESTMENT_TRADING", "en", "NUMBER_ONLY"),
    ("Earn 10% daily guaranteed profit in stock market. Message me on Telegram for insider tips.", "INVESTMENT_TRADING", "en", "NUMBER_ONLY"),
    ("गारंटीड रिटर्न: 7 दिन में पैसे डबल करें। शेयर बाजार वीआईपी ग्रुप से जुड़ें: https://t.me/stock_tips", "INVESTMENT_TRADING", "hi", "NUMBER_ONLY"),
    ("Paisa double karo 10 din me guaranteed profit. Telegram par message karo: https://t.me/trading_tips", "INVESTMENT_TRADING", "hi-Latn", "NUMBER_ONLY"),

    # LOTTERY_PRIZE
    ("Congratulations! You won Rs 25 Lakhs in KBC Lucky Draw. Call manager to claim prize.", "LOTTERY_PRIZE", "en", "NUMBER_ONLY"),
    ("बधाई हो! आपने केबीसी लकी ड्रा में 25 लाख रुपये जीते हैं। इनाम पाने के लिए संपर्क करें।", "LOTTERY_PRIZE", "hi", "NUMBER_ONLY"),
    ("Aapne jita hai 25 lakh ka lottery prize. Turant contact karo claim karne ke liye.", "LOTTERY_PRIZE", "hi-Latn", "NUMBER_ONLY"),

    # LOAN_CREDIT
    ("Pre-approved instant personal loan of Rs 5,00,000 with zero documents. Click to apply: http://fast-loan.top", "LOAN_CREDIT", "en", "NUMBER_ONLY"),
    ("तुरंत 5 लाख का पर्सनल लोन बिना किसी दस्तावेज के स्वीकृत। आवेदन करें: http://easy-loan.click", "LOAN_CREDIT", "hi", "NUMBER_ONLY"),
    ("Pre-approved instant loan 3 lakh bina kisi document ke. Apply karein: http://quick-loan.xyz", "LOAN_CREDIT", "hi-Latn", "NUMBER_ONLY"),

    # DELIVERY_COURIER
    ("India Post: Delivery failed due to incomplete address. Update address within 24 hours: http://indiapost-parcel.xyz", "DELIVERY_COURIER", "en", "NUMBER_ONLY"),
    ("पार्सल डिलीवरी फेल: गलत पते के कारण पार्सल रुका हुआ है। पता अपडेट करें: http://parcel-update.top", "DELIVERY_COURIER", "hi", "NUMBER_ONLY"),
    ("Delivery fail ho gayi hai address incomplete hone se. Update karein: http://courier-track.xyz", "DELIVERY_COURIER", "hi-Latn", "NUMBER_ONLY"),

    # IMPERSONATED_CONTACT
    ("Hi, this is my new number. I lost my phone and stuck in emergency. Please send Rs 5000 urgently.", "IMPERSONATED_CONTACT", "en", "NUMBER_ONLY"),
    ("नमस्ते, यह मेरा नया नंबर है। अस्पताल में इमरजेंसी है, तुरंत 5000 रुपये भेजें।", "IMPERSONATED_CONTACT", "hi", "NUMBER_ONLY"),
    ("Hey yeh mera naya number hai. Emergency me hu please 5000 transfer karo turant.", "IMPERSONATED_CONTACT", "hi-Latn", "NUMBER_ONLY"),
]

benign_templates = [
    # Genuine bank debit / credit alerts
    ("Dear customer, Rs 450.00 debited from A/C XX1234 on 05-Oct-26 at POS SWIGGY. Avail Bal: Rs 15,240.50.", "BANK", "en", "NAMED"),
    ("Your A/C XX5678 is credited by Rs 25,000.00 on 01-Oct-26 by salary transfer. Net available balance: Rs 42,100.", "BANK", "en", "NAMED"),
    ("HDFC Bank Alert: Transaction of Rs. 1,200.00 on your Credit Card ending 9876 at AMAZON INDIA was successful.", "BANK", "en", "NAMED"),
    ("ICICI Bank: Rs 500 transferred to Ramesh via UPI. Ref No 4291048201. Bal: Rs 8,300.", "BANK", "en", "NAMED"),
    ("आपके खाते XX4321 से ₹350.00 का भुगतान सफल रहा। शेष राशि: ₹12,400.00", "BANK", "hi", "NAMED"),
    ("Aapke account XX7890 me Rs 5,000 credit hua hai via UPI. Available balance: Rs 18,200.", "BANK", "hi-Latn", "NAMED"),

    # Genuine OTP deliveries (with do not share dampener B01)
    ("482910 is your OTP for transaction of Rs 1,499 at Zomato. Do not share with anyone.", "BANK", "en", "NAMED"),
    ("Your One Time Password (OTP) for HDFC NetBanking login is 839201. Never share this code with anyone.", "BANK", "en", "NAMED"),
    ("OTP for Aadhaar authentication is 392048. Do not share with anyone.", "GOVERNMENT", "en", "NAMED"),
    ("एसबीआई नेटबैंकिंग लॉगिन के लिए आपका ओटीपी 592014 है। इसे किसी के साथ साझा न करें।", "BANK", "hi", "NAMED"),
    ("Zomato login ke liye OTP 392018 hai. Kisi ke sath share na karein.", "OTHER", "hi-Latn", "NAMED"),

    # Genuine courier updates
    ("Your Amazon package with tracking ID 482019482 is out for delivery today with delivery associate Rajesh.", "COURIER", "en", "NAMED"),
    ("Flipkart: Order containing Bluetooth Earphones has been delivered successfully. Thank you for shopping with us.", "COURIER", "en", "NAMED"),
    ("India Post: Consignment EK492018492IN has reached Mumbai NSH. Expected delivery by tomorrow.", "COURIER", "en", "NAMED"),
    ("आपका फ्लिपकार्ट ऑर्डर आज डिलीवर हो जाएगा। डिलीवरी बॉय आपसे संपर्क करेगा।", "COURIER", "hi", "NAMED"),
    ("Aapka Amazon parcel out for delivery hai. Agent ka contact number: 9876543210.", "COURIER", "hi-Latn", "NAMED"),

    # Genuine utility & telecom bills
    ("Dear customer, your JioFiber bill for Oct is generated: Rs 470.82. Due date 18-Oct-26. Visit jio.com to pay.", "UTILITY", "en", "NAMED"),
    ("Airtel postpaid bill of Rs 588.82 for mobile 9876543210 is due on 14-Oct-26. Pay via airtel.in or Airtel Thanks app.", "TELECOM", "en", "NAMED"),
    ("BSES Yamuna: Electricity bill of Rs 1,840 for CA 102938472 is due on 20-Oct-26. Pay on bsesdelhi.com.", "UTILITY", "en", "NAMED"),
    ("आपके जियो नंबर का रिचार्ज 3 दिन में समाप्त होगा। myjio ऐप से रिचार्ज करें।", "TELECOM", "hi", "NAMED"),

    # Genuine wedding / event invitations (clean text)
    ("We cordially invite you and your family to the auspicious wedding ceremony of our son Rahul with Priya on 25th Nov at Taj Palace, New Delhi.", "INVITE", "en", "NAMED"),
    ("You are warmly invited to celebrate the 1st birthday of little Aarav this Saturday 6 PM at our residence. Dinner to follow.", "INVITE", "en", "NAMED"),
    ("सादर निमंत्रण: आप सपरिवार हमारे सुपुत्र रोहन के शुभ विवाह में सादर आमंत्रित हैं। दिनांक: 20 नवंबर।", "INVITE", "hi", "NAMED"),
    ("Humare bete ki shadi me aap sabhi parivar sahit aamantrit hain. Venue: Grand Hotel, Jaipur.", "INVITE", "hi-Latn", "NAMED"),

    # Normal conversations between friends/family
    ("Hey, are you free this weekend? Let's catch up for lunch!", "CHAT", "en", "NAMED"),
    ("Can you send me notes for yesterday's lecture? Missed the class.", "CHAT", "en", "NAMED"),
    ("Happy Diwali to you and your family! May the divine light bring joy and good health.", "FESTIVAL", "en", "NAMED"),
    ("Hey bro, can you transfer 500 on GPay? Will give you back in evening.", "CHAT", "en", "NAMED"),
    ("Kya haal hai bhai? Sham ko milte hai chai pe.", "CHAT", "hi-Latn", "NAMED"),
    ("Kal office kab aaoge? Sath me lunch karte hai.", "CHAT", "hi-Latn", "NAMED"),
    ("शुभ दीपावली! आपके और आपके परिवार के लिए यह पावन पर्व मंगलमय हो।", "FESTIVAL", "hi", "NAMED"),
    ("नमस्ते, आज शाम को मीटिंग है क्या? कृपया समय बताएं।", "CHAT", "hi", "NAMED"),
    ("Bhai project ki presentation ready ho gayi kya? Ek bar check kar lena.", "CHAT", "hi-Latn", "NAMED"),
    ("Did you book the train tickets for Diwali? Let me know the seat numbers.", "CHAT", "en", "NAMED"),
    ("Mom said dinner is ready. Come home early today.", "CHAT", "en", "NAMED"),
    ("Beware of fake messages asking for electricity bill payments or OTPs. Stay alert!", "SECURITY_WARNING", "en", "NAMED")
]

# Generate rows with variations
scam_rows = []
for i in range(350):
    tpl, cat, lang, sender = scam_templates[i % len(scam_templates)]
    # Add slight random variations
    text = tpl
    if i % 3 == 0:
        text += f" Ref: {random.randint(1000, 9999)}"
    scam_rows.append({
        "id": f"scam-{i+1:04d}",
        "text": text,
        "label": "scam",
        "category": cat,
        "lang": lang,
        "sender_kind": sender,
        "is_group": False,
        "origin": "synthetic",
        "group_id": f"scam-grp-{i % len(scam_templates)}"
    })

benign_rows = []
for i in range(650):
    tpl, cat, lang, sender = benign_templates[i % len(benign_templates)]
    text = tpl
    if i % 4 == 0:
        text += f" Ref ID: {random.randint(100000, 999999)}"
    benign_rows.append({
        "id": f"benign-{i+1:04d}",
        "text": text,
        "label": "benign",
        "category": cat,
        "lang": lang,
        "sender_kind": sender,
        "is_group": False,
        "origin": "synthetic",
        "group_id": f"benign-grp-{i % len(benign_templates)}"
    })

all_rows = scam_rows + benign_rows
random.shuffle(all_rows)

with open("eval/corpus.jsonl", "w", encoding="utf-8") as f:
    for row in all_rows:
        f.write(json.dumps(row, ensure_ascii=False) + "\n")

print(f"Generated {len(all_rows)} rows ({len(scam_rows)} scam, {len(benign_rows)} benign) in eval/corpus.jsonl")
