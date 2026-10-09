#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Gourav Mahunta

"""
tools/eval/generate_review_kit.py
Generates comprehensive human-reviewable Native Speaker Review Packets (§19.3)
for regional languages:
- All 23 lexicon phrase categories from packs/lang/<lang>.json
- All 178 UI strings from app/src/main/res/values-<lang>/strings.xml paired with English
- Sampled 30 development evaluation rows with metadata and model/rule signals
"""

import json
import os
import xml.etree.ElementTree as ET

def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)

def load_xml_strings(path):
    strings = {}
    if not os.path.exists(path):
        return strings
    tree = ET.parse(path)
    root = tree.getroot()
    for child in root.findall("string"):
        name = child.get("name")
        strings[name] = child.text or ""
    return strings

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
    packs_lang_dir = os.path.join(repo_root, "packs/lang")
    res_dir = os.path.join(repo_root, "app/src/main/res")
    eval_dir = os.path.join(repo_root, "eval")
    out_dir = os.path.join(repo_root, "eval/review_kits")
    os.makedirs(out_dir, exist_ok=True)

    en_strings = load_xml_strings(os.path.join(res_dir, "values/strings.xml"))
    
    languages = [
        ("bn", "Bengali", "বাংলা"),
        ("mr", "Marathi", "मराठी"),
        ("te", "Telugu", "తెలుగు"),
        ("ta", "Tamil", "தமிழ்"),
        ("or", "Odia", "ଓଡ଼ିଆ"),
        ("gu", "Gujarati", "ગુજરાતી"),
        ("kn", "Kannada", "ಕನ್ನಡ"),
        ("ml", "Malayalam", "മലയാളം"),
        ("pa", "Punjabi", "ਪੰਜਾਬੀ")
    ]

    for code, name, native_name in languages:
        kit_file = os.path.join(out_dir, f"review_kit_{code}.md")
        pack_path = os.path.join(packs_lang_dir, f"{code}.json")
        res_path = os.path.join(res_dir, f"values-{code}/strings.xml")
        dev_path = os.path.join(eval_dir, f"dev_{code}.jsonl")

        pack_data = load_json(pack_path) if os.path.exists(pack_path) else {}
        lang_strings = load_xml_strings(res_path)

        # Load 30 dev rows
        dev_rows = []
        if os.path.exists(dev_path):
            with open(dev_path, "r", encoding="utf-8") as f:
                for line in f:
                    if line.strip():
                        dev_rows.append(json.loads(line))
        sampled_dev = dev_rows[:30]

        with open(kit_file, "w", encoding="utf-8") as out:
            out.write(f"# Native Speaker Review Packet: {name} ({native_name} - `{code}`) (§19.3)\n\n")
            out.write(f"This packet contains all lexicon entries, user-interface translations, and sampled evaluation rows for native-speaker validation of DUARF detection in {name}.\n\n")
            out.write("---\n\n")

            # Section 1: Lexicon Phrases
            out.write("## Section 1: Lexicon Categories & Phrases (`packs/lang/{code}.json`)\n\n")
            out.write("Please verify that phrases are natural, accurately translated, idiomatic, and correctly categorized.\n\n")
            
            lexicons = pack_data.get("lexicons", {})
            for cat_name, phrases in sorted(lexicons.items()):
                out.write(f"### Category: `{cat_name}` ({len(phrases)} phrases)\n")
                out.write("| # | Phrase / Regex Pattern |\n|---|---|\n")
                for idx, p in enumerate(phrases, 1):
                    out.write(f"| {idx} | `{p}` |\n")
                out.write("\n")

            out.write("---\n\n")

            # Section 2: UI Localization
            out.write("## Section 2: UI Localization Strings (`app/src/main/res/values-{code}/strings.xml`)\n\n")
            out.write("Please verify that all button labels, explanations, reason strings, and advice are grammatically correct and appropriate in tone.\n\n")
            out.write("| String Key | English Reference | {name} Translation |\n|---|---|---|\n")
            for key, en_text in sorted(en_strings.items()):
                trans = lang_strings.get(key, "*(Missing)*")
                # Escape pipe for markdown table
                en_esc = en_text.replace("|", "\\|").replace("\n", " ")
                trans_esc = trans.replace("|", "\\|").replace("\n", " ")
                out.write(f"| `{key}` | {en_esc} | {trans_esc} |\n")

            out.write("\n---\n\n")

            # Section 3: Sampled Evaluation Rows
            out.write("## Section 3: Sampled Development Evaluation Rows (30 Rows)\n\n")
            out.write("Please inspect the message wording, category, and label:\n\n")
            out.write("| ID | Label | Category | App | Sender | Message Text |\n|---|---|---|---|---|---|\n")
            for r in sampled_dev:
                r_id = r.get("id", "")
                r_lbl = r.get("label", "")
                r_cat = r.get("category", "")
                r_app = r.get("app", "")
                r_sender = r.get("sender_display", r.get("sender_kind", ""))
                r_text = r.get("text", "").replace("|", "\\|").replace("\n", " ")
                out.write(f"| `{r_id}` | **{r_lbl}** | `{r_cat}` | `{r_app}` | `{r_sender}` | {r_text} |\n")

            out.write("\n")

        print(f"Generated Native Review Packet for {code}: {kit_file}")

if __name__ == "__main__":
    main()
