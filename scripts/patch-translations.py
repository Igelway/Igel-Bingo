#!/usr/bin/env python3
"""
Generate BOTH de_de.json and en_us.json for the resource pack.
Reads BAC advancement JSONs to get actual translation keys,
maps them to German (from old de_de.json) and English (generated).
Also includes original English text as en_us.json for completeness.
"""
import json
import os
import sys

BAC_DIR = sys.argv[1] if len(sys.argv) > 1 else "docker/bac-datapack"
OLD_DE = sys.argv[2] if len(sys.argv) > 2 else "igelbingo-game/src/main/resources/resourcepack/assets/minecraft/lang/de_de.json"
OUT_DIR = sys.argv[3] if len(sys.argv) > 3 else "/tmp/rp-out"

os.makedirs(os.path.join(OUT_DIR, "assets", "minecraft", "lang"), exist_ok=True)

# Load old German translations (English text → German text)
with open(OLD_DE, 'r', encoding='utf-8') as f:
    old_translations = json.load(f)

def key_to_title(key):
    """Convert namespace key to human-readable English title."""
    text = key
    for suffix in ['.title', '.description']:
        if text.endswith(suffix):
            text = text[:-len(suffix)]
    parts = text.split('.')
    name = parts[-1] if parts else text
    words = name.replace('_', ' ').split()
    return ' '.join(w.capitalize() for w in words)

de_translations = {}
en_translations = {}

def process_adv_file(path):
    try:
        with open(path, 'r', encoding='utf-8') as fp:
            data = json.load(fp)
    except Exception:
        return

    display = data.get('display', {})
    for field in ['title', 'description']:
        field_data = display.get(field, {})
        key = field_data.get('translate', '')
        if not key:
            continue

        # Try to find existing German translation
        german = old_translations.get(key)
        if german is None:
            english = key_to_title(key)
            german = old_translations.get(english)
        if german:
            de_translations[key] = german
            en_translations[key] = key_to_title(key)

# Scan BAC advancements
for base in ['blazeandcave', 'minecraft']:
    adv_dir = os.path.join(BAC_DIR, 'data', base, 'advancement')
    if not os.path.isdir(adv_dir):
        continue
    for root, dirs, files in os.walk(adv_dir):
        for f in files:
            if f.endswith('.json'):
                process_adv_file(os.path.join(root, f))

# Write de_de.json
with open(os.path.join(OUT_DIR, 'assets', 'minecraft', 'lang', 'de_de.json'), 'w', encoding='utf-8') as f:
    json.dump(de_translations, f, ensure_ascii=False, indent=2)

# Write en_us.json (maps keys to English display text)
with open(os.path.join(OUT_DIR, 'assets', 'minecraft', 'lang', 'en_us.json'), 'w', encoding='utf-8') as f:
    json.dump(en_translations, f, ensure_ascii=False, indent=2)

# Copy pack.mcmeta
meta = {"pack": {"description": "Igel-Bingo: BAC translations (de+en)", "min_format": [101, 1], "max_format": [101, 1]}}
with open(os.path.join(OUT_DIR, 'pack.mcmeta'), 'w', encoding='utf-8') as f:
    json.dump(meta, f)

print(f"German: {len(de_translations)} keys, English: {len(en_translations)} keys")
