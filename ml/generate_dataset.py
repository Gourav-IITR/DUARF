import json
import os
import random
import re

SEED = 42
random.seed(SEED)

BRANDS_BANK = ["SBI", "HDFC Bank", "ICICI Bank", "Axis Bank", "Punjab National Bank", "Kotak Mahindra Bank", "Bank of Baroda", "Canara Bank", "Union Bank", "IndusInd Bank"]
BRANDS_TELECOM = ["Jio", "Airtel", "Vi", "BSNL"]
BRANDS_PAYMENT = ["PhonePe", "Google Pay", "Paytm", "BHIM UPI", "Cred", "Amazon Pay"]
BRANDS_ECOMMERCE = ["Amazon", "Flipkart", "Myntra", "Meesho", "Blinkit", "Zepto", "Instamart", "BigBasket"]

FILE_PREFIXES = ["wedding_invitation", "shadi_card", "echallan_pay", "traffic_fine", "update_service", "indiapost_parcel", "pan_verification", "bill_desk", "gas_subsidy", "bank_security", "reward_claim", "police_notice"]
TLDS = ["xyz", "top", "click", "buzz", "site", "online", "live", "vip", "club"]
SHORTENERS = ["bit.ly", "tinyurl.com", "is.gd", "t.co", "cutt.ly"]
POS_MERCHANTS = ["SWIGGY", "ZOMATO", "AMAZON PAY", "FLIPKART", "APOLLO PHARMACY", "SHELL PETROL", "RELIANCE FRESH", "DMART", "BIGBASKET", "BOOKMYSHOW", "INDIAN OIL", "UBER RIDE", "OLA CABS", "BLINKIT", "ZEPTO", "IRCTC", "MYNTRA", "DECATHLON", "STARBUCKS", "MCDONALDS"]
PLACES = ["Connaught Place", "Cyber Hub", "MG Road", "Khan Market", "South Ex", "Indiranagar", "Koramangala", "Bandra West", "Hauz Khas", "Sector 18", "Salt Lake", "Jubilee Hills", "FC Road", "Whitefield", "Park Street"]
MONTHS = ["September 2026", "October 2026", "August 2026", "July 2026", "November 2026"]
NAMES = ["Rahul", "Amit", "Vikram", "Priya", "Suresh", "Rohan", "Anjali", "Pooja", "Neha", "Manish", "Kavita", "Deepak", "Sunil", "Rajesh", "Gaurav", "Sneha", "Kunal", "Meera", "Arjun", "Aditya"]
DRIVERS = ["Ramesh", "Mukesh", "Sandeep", "Deepak", "Sunil", "Manoj", "Ajay", "Pradeep", "Dharmendra", "Santosh", "Vijay"]
UPI_HANDLES = ["okaxis", "okhdfcbank", "paytm", "ybl", "okicici", "ibl", "axl"]
TELEGRAM_USERS = ["task_manager_daily", "vip_stock_insider", "crypto_signals_pvt", "parttime_hr_office", "wealth_growth_club", "earn_daily_fast", "gold_trading_hub", "youtube_tasks_india"]

SUFFIX_PHRASES = [
    "", " Ref: TXN{ref_id}", " Txn ID: {ref_id}", " Helpline: 1800-{rand4}-{rand4}",
    " Please check.", " Urgent notice.", " Thank you.", " Have a nice day.",
    " Regards.", " Do not ignore.", " Contact support if needed.",
    " Kripya dhyan dein.", " Shubh din.", " Dhanyawad."
]

def fill_slots(template_str):
    res = template_str
    bank = random.choice(BRANDS_BANK)
    bank_clean = bank.lower().replace(" ", "").replace("bank", "")
    tld = random.choice(TLDS)
    rand_num = random.randint(10, 999)
    res = res.replace("{brand_bank}", bank)
    res = res.replace("{brand_telecom}", random.choice(BRANDS_TELECOM))
    res = res.replace("{brand_payment}", random.choice(BRANDS_PAYMENT))
    res = res.replace("{ecommerce_brand}", random.choice(BRANDS_ECOMMERCE))
    
    file_apk = f"{random.choice(FILE_PREFIXES)}_{rand_num}.apk"
    if random.random() < 0.3:
        file_apk = f"{random.choice(FILE_PREFIXES)}.pdf.apk"
    res = res.replace("{file_apk}", file_apk)
    
    res = res.replace("{lookalike_domain}", f"{bank_clean}-portal-{rand_num}.{tld}")
    res = res.replace("{domain}", f"notice-service-{rand_num}.{tld}")
    res = res.replace("{shortener}", random.choice(SHORTENERS))
    
    amount = random.randint(100, 49900)
    res = res.replace("{amount}", str(amount))
    res = res.replace("{avail_bal}", str(amount * random.randint(2, 8) + random.randint(10, 99)))
    res = res.replace("{loan_amount}", f"{random.choice([1, 2, 3, 5, 8, 10])},00,000")
    res = res.replace("{pos_merchant}", random.choice(POS_MERCHANTS))
    res = res.replace("{place}", random.choice(PLACES))
    
    day = random.randint(1, 28)
    month_abbr = random.choice(["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct"])
    res = res.replace("{date}", f"{day:02d}-{month_abbr}-26")
    res = res.replace("{due_date}", f"{random.randint(1, 28):02d}-{month_abbr}")
    res = res.replace("{bill_month}", random.choice(MONTHS))
    res = res.replace("{name}", random.choice(NAMES))
    res = res.replace("{driver_name}", random.choice(DRIVERS))
    
    user_name = random.choice(NAMES).lower()
    res = res.replace("{upi_id}", f"{user_name}{rand_num}@{random.choice(UPI_HANDLES)}")
    res = res.replace("{telegram_user}", f"{random.choice(TELEGRAM_USERS)}_{rand_num}")
    res = res.replace("{phone}", f"+9198{random.randint(10000000, 99999999)}")
    res = res.replace("{acct_num}", f"{random.randint(1000000000, 9999999999)}")
    res = res.replace("{acct_last4}", f"{random.randint(1000, 9999)}")
    res = res.replace("{aadhaar_last4}", f"{random.randint(1000, 9999)}")
    res = res.replace("{veh_num}", f"DL{random.randint(1, 9)}C{random.choice('ABCDEF')}{random.randint(1000, 9999)}")
    res = res.replace("{consumer_id}", f"CA{random.randint(10000000, 99999999)}")
    res = res.replace("{tracking_id}", f"IN{random.randint(100000000, 999999999)}P")
    res = res.replace("{order_id}", f"OD{random.randint(10000000, 99999999)}")
    res = res.replace("{ref_id}", f"CMS{random.randint(10000000, 99999999)}")
    res = res.replace("{otp_code}", f"{random.randint(100000, 999999)}")
    res = res.replace("{deliv_pin}", f"{random.randint(1000, 9999)}")
    res = res.replace("{time}", f"{random.randint(1, 12)}:{random.randint(10, 59)} PM")
    res = res.replace("{slug}", f"r{rand_num}")
    
    # Optional variation suffix
    suffix = random.choice(SUFFIX_PHRASES)
    if suffix:
        suffix = suffix.replace("{ref_id}", str(random.randint(1000000, 9999999)))
        suffix = suffix.replace("{rand4}", str(random.randint(1000, 9999)))
        res = res + suffix
        
    return res

def expand_template(tpl, target_count, label, existing_texts):
    rows = []
    attempts = 0
    max_attempts = target_count * 100
    while len(rows) < target_count and attempts < max_attempts:
        attempts += 1
        filled = fill_slots(tpl["text"])
        norm = " ".join(filled.strip().split())
        if norm in existing_texts:
            continue
        existing_texts.add(norm)

        row = {
            "id": f"{tpl['id']}-{len(rows) + 1:04d}",
            "text": filled,
            "label": label,
            "category": tpl.get("category", "OTHER_SUSPICIOUS"),
            "lang": tpl.get("lang", "en"),
            "sender_kind": tpl.get("sender_kind", "NUMBER_ONLY"),
            "is_group": False,
            "origin": "synthetic",
            "group_id": tpl["id"],
            "notes": "adversarial" if tpl.get("adversarial", False) else ""
        }
        rows.append(row)
    return rows

def generate():
    with open("ml/templates/train_templates.json", "r", encoding="utf-8") as f:
        train_data = json.load(f)
    with open("ml/templates/heldout_test_templates.json", "r", encoding="utf-8") as f:
        test_data = json.load(f)

    os.makedirs("ml/data", exist_ok=True)
    all_seen_texts = set()

    # Load seed corpus texts so they are strictly excluded
    seed_corpus_path = "eval/corpus.jsonl"
    if os.path.exists(seed_corpus_path):
        with open(seed_corpus_path, "r", encoding="utf-8") as f:
            for line in f:
                r = json.loads(line)
                all_seen_texts.add(" ".join(r["text"].strip().split()))
        print(f"Loaded {len(all_seen_texts)} seed corpus texts to strictly exclude from training/dev/test.")

    # 1. Generate Test Split from independent heldout test templates
    test_rows = []
    test_scam_tpls = test_data["scam_templates"]
    test_benign_tpls = test_data["benign_templates"]

    test_target_scam = 1200
    test_target_benign = 2000 # Total ~3200 (62.5% benign)

    per_scam_test = test_target_scam // len(test_scam_tpls) + 5
    per_benign_test = test_target_benign // len(test_benign_tpls) + 5

    for tpl in test_scam_tpls:
        test_rows.extend(expand_template(tpl, per_scam_test, "scam", all_seen_texts))
    for tpl in test_benign_tpls:
        test_rows.extend(expand_template(tpl, per_benign_test, "benign", all_seen_texts))

    random.shuffle(test_rows)
    # Trim to exact proportions if needed
    test_scams = [r for r in test_rows if r["label"] == "scam"][:1200]
    test_benign = [r for r in test_rows if r["label"] == "benign"][:2000]
    final_test = test_scams + test_benign
    random.shuffle(final_test)

    # 2. Generate Train and Dev from train templates, splitting by group_id
    train_scam_tpls = train_data["scam_templates"]
    train_benign_tpls = train_data["benign_templates"]

    # Assign entire group_ids to dev split (~12% of templates)
    random.shuffle(train_scam_tpls)
    random.shuffle(train_benign_tpls)

    dev_scam_count = max(3, int(len(train_scam_tpls) * 0.12))
    dev_benign_count = max(3, int(len(train_benign_tpls) * 0.12))

    dev_scam_tpls = train_scam_tpls[:dev_scam_count]
    tr_scam_tpls = train_scam_tpls[dev_scam_count:]

    dev_benign_tpls = train_benign_tpls[:dev_benign_count]
    tr_benign_tpls = train_benign_tpls[dev_benign_count:]

    # Expand Train
    # Target: ~8,000 scam, ~12,000 benign (Total 20,000, 60% benign)
    train_target_scam = 8000
    train_target_benign = 12000

    per_scam_train = train_target_scam // len(tr_scam_tpls) + 5
    per_benign_train = train_target_benign // len(tr_benign_tpls) + 5

    train_rows = []
    for tpl in tr_scam_tpls:
        train_rows.extend(expand_template(tpl, per_scam_train, "scam", all_seen_texts))
    for tpl in tr_benign_tpls:
        train_rows.extend(expand_template(tpl, per_benign_train, "benign", all_seen_texts))

    random.shuffle(train_rows)
    tr_scams = [r for r in train_rows if r["label"] == "scam"][:8000]
    tr_benign = [r for r in train_rows if r["label"] == "benign"][:12000]
    final_train = tr_scams + tr_benign
    random.shuffle(final_train)

    # Expand Dev
    # Target: ~1,000 scam, ~1,500 benign (Total 2,500, 60% benign)
    dev_target_scam = 1000
    dev_target_benign = 1500

    per_scam_dev = dev_target_scam // len(dev_scam_tpls) + 5
    per_benign_dev = dev_target_benign // len(dev_benign_tpls) + 5

    dev_rows = []
    for tpl in dev_scam_tpls:
        dev_rows.extend(expand_template(tpl, per_scam_dev, "scam", all_seen_texts))
    for tpl in dev_benign_tpls:
        dev_rows.extend(expand_template(tpl, per_benign_dev, "benign", all_seen_texts))

    random.shuffle(dev_rows)
    dev_scams = [r for r in dev_rows if r["label"] == "scam"][:1000]
    dev_benign = [r for r in dev_rows if r["label"] == "benign"][:1500]
    final_dev = dev_scams + dev_benign
    random.shuffle(final_dev)

    # Write splits
    def write_jsonl(path, rows):
        with open(path, "w", encoding="utf-8") as f:
            for r in rows:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")

    write_jsonl("ml/data/train.jsonl", final_train)
    write_jsonl("ml/data/dev.jsonl", final_dev)
    write_jsonl("ml/data/test.jsonl", final_test)

    # Verification and Statistics Reporting
    def print_stats(name, rows):
        distinct_texts = len(set(r["text"] for r in rows))
        group_ids = set(r["group_id"] for r in rows)
        scams = sum(1 for r in rows if r["label"] == "scam")
        benign = sum(1 for r in rows if r["label"] == "benign")
        langs = {}
        for r in rows:
            langs[r["lang"]] = langs.get(r["lang"], 0) + 1
        adv = sum(1 for r in rows if r.get("notes") == "adversarial")

        print(f"=== {name} Statistics ===")
        print(f"Total rows: {len(rows)}")
        print(f"Distinct texts: {distinct_texts}")
        print(f"Unique templates (group_ids): {len(group_ids)}")
        print(f"Class distribution: {scams} scam ({scams/len(rows)*100:.1f}%), {benign} benign ({benign/len(rows)*100:.1f}%)")
        print(f"Language distribution: {langs}")
        print(f"Adversarial examples: {adv}\n")
        return group_ids

    train_groups = print_stats("TRAIN SPLIT", final_train)
    dev_groups = print_stats("DEV SPLIT", final_dev)
    test_groups = print_stats("TEST SPLIT", final_test)

    # Assert no group_id leakage across splits
    assert len(train_groups.intersection(dev_groups)) == 0, "Train and Dev share group_ids!"
    assert len(train_groups.intersection(test_groups)) == 0, "Train and Test share group_ids!"
    assert len(dev_groups.intersection(test_groups)) == 0, "Dev and Test share group_ids!"
    print("SUCCESS: Zero group_id overlap across train, dev, and test splits.")

if __name__ == "__main__":
    generate()
