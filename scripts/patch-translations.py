#!/usr/bin/env python3
"""
Patch resource pack translations for BAC advancements.
BAC uses namespace keys like 'advancements.building.its_a_sign.title'
but the old de_de.json maps English text like 'It's a Sign'.
This script generates a proper de_de.json that matches BAC's actual keys.
"""
import json
import os
import re
import sys

BAC_DIR = sys.argv[1] if len(sys.argv) > 1 else "docker/bac-datapack"
OLD_DE = sys.argv[2] if len(sys.argv) > 2 else "igelbingo-game/src/main/resources/resourcepack/assets/minecraft/lang/de_de.json"
OUTPUT = sys.argv[3] if len(sys.argv) > 3 else "docker/IgelBingo_Resources.json"

# Load old German translations (English text → German text)
with open(OLD_DE, 'r', encoding='utf-8') as f:
    old_translations = json.load(f)

# Generate English text from namespace key
def key_to_english(key):
    # 'advancements.building.its_a_sign.title' → 'Its A Sign'
    # Remove namespace prefix and .title/.description suffix
    text = key
    for suffix in ['.title', '.description']:
        if text.endswith(suffix):
            text = text[:-len(suffix)]
    # Get the last part after the last dot
    parts = text.split('.')
    name = parts[-1] if parts else text
    # Convert snake_case to Title Case
    words = name.replace('_', ' ').split()
    return ' '.join(w.capitalize() for w in words)

# Collect all BAC advancement translation keys
translations = {}

def collect_advancements(base_dir):
    adv_dir = os.path.join(base_dir, 'data', 'blazeandcave', 'advancement')
    if not os.path.isdir(adv_dir):
        return
    for root, dirs, files in os.walk(adv_dir):
        for f in files:
            if not f.endswith('.json'):
                continue
            path = os.path.join(root, f)
            try:
                with open(path, 'r', encoding='utf-8') as fp:
                    data = json.load(fp)
            except (json.JSONDecodeError, Exception):
                continue

            display = data.get('display', {})
            for field in ['title', 'description']:
                field_data = display.get(field, {})
                key = field_data.get('translate', '')
                if not key:
                    continue
                # Try to find existing German translation
                german = old_translations.get(key, None)
                if german is None:
                    # Try matching by English text approximation
                    english = key_to_english(key)
                    german = old_translations.get(english, None)
                if german:
                    translations[key] = german

    # Also collect vanilla advancement keys
    vanilla_dir = os.path.join(base_dir, 'data', 'minecraft', 'advancement')
    if os.path.isdir(vanilla_dir):
        for root, dirs, files in os.walk(vanilla_dir):
            for f in files:
                if not f.endswith('.json'):
                    continue
                path = os.path.join(root, f)
                try:
                    with open(path, 'r', encoding='utf-8') as fp:
                        data = json.load(fp)
                except (json.JSONDecodeError, Exception):
                    continue

                display = data.get('display', {})
                for field in ['title', 'description']:
                    field_data = display.get(field, {})
                    key = field_data.get('translate', '')
                    if not key:
                        continue
                    german = old_translations.get(key, None)
                    if german is None:
                        english = key_to_english(key)
                        german = old_translations.get(english, None)
                    if german:
                        translations[key] = german

collect_advancements(BAC_DIR)

print(f"Found {len(translations)} translatable keys")

# Write output
with open(OUTPUT, 'w', encoding='utf-8') as f:
    json.dump(translations, f, ensure_ascii=False, indent=2)

print(f"Written to {OUTPUT}")
