#!/bin/bash
set -e

# Downloads the latest BlazeandCave's Advancements Pack from Modrinth,
# filters it to only keep advancement files, and installs to the datapacks directory.
#
# BAC is downloaded at runtime (not bundled) to comply with its license:
#   "Re-uploading or re-distributing this datapack in any form is PROHIBITED"
# The filter script IS our code (MIT) and CAN be bundled.

BAC_DATAPACK_DIR="${1:-/data/world/datapacks/BAC_Filtered}"
WORK_DIR="/tmp/bac-filter-$$"

echo "=== BAC Filter ==="
echo "Downloading BlazeandCave's Advancements Pack from Modrinth..."

mkdir -p "$WORK_DIR"
mkdir -p "$BAC_DATAPACK_DIR"

# Download latest BAC for 1.21.5 from Modrinth API
# Version 1.18.3 (latest for 1.21.5 as of 2025-06)
BAC_URL="https://cdn.modrinth.com/data/VoVJ47kN/versions/3SdMv4Dr/BlazeandCave%27s%20Advancements%20Pack%201.18.3.zip"

if command -v curl &>/dev/null; then
    curl -fSL -o "$WORK_DIR/bac.zip" "$BAC_URL"
elif command -v wget &>/dev/null; then
    wget -O "$WORK_DIR/bac.zip" "$BAC_URL"
else
    echo "ERROR: Neither curl nor wget found"
    exit 1
fi

echo "Extracting and filtering..."
unzip -qo "$WORK_DIR/bac.zip" -d "$WORK_DIR/extracted"

# Clean previous filtered output
rm -rf "$BAC_DATAPACK_DIR"
mkdir -p "$BAC_DATAPACK_DIR"

# Copy pack metadata
cp "$WORK_DIR/extracted/pack.mcmeta" "$BAC_DATAPACK_DIR/" 2>/dev/null || true
cp "$WORK_DIR/extracted/pack.png" "$BAC_DATAPACK_DIR/" 2>/dev/null || true

# Copy ONLY advancement files (the only thing BingoReloaded needs)
# Keep vanilla overrides (they reference blazeandcave tabs)
if [ -d "$WORK_DIR/extracted/data/minecraft/advancement" ]; then
    mkdir -p "$BAC_DATAPACK_DIR/data/minecraft"
    cp -r "$WORK_DIR/extracted/data/minecraft/advancement" "$BAC_DATAPACK_DIR/data/minecraft/"
fi

# Keep BAC advancements
if [ -d "$WORK_DIR/extracted/data/blazeandcave/advancement" ]; then
    mkdir -p "$BAC_DATAPACK_DIR/data/blazeandcave"
    cp -r "$WORK_DIR/extracted/data/blazeandcave/advancement" "$BAC_DATAPACK_DIR/data/blazeandcave/"
fi

# Copy advancement-message files (used for German translations via resource pack)
if [ -d "$WORK_DIR/extracted/data/blazeandcave/msg" ]; then
    mkdir -p "$BAC_DATAPACK_DIR/data/blazeandcave"
    cp -r "$WORK_DIR/extracted/data/blazeandcave/msg" "$BAC_DATAPACK_DIR/data/blazeandcave/"
fi

# Count filtered files
ADV_COUNT=$(find "$BAC_DATAPACK_DIR" -name "*.json" | wc -l)
echo "BAC Filter complete: $ADV_COUNT advancement files installed to $BAC_DATAPACK_DIR"

# Cleanup
rm -rf "$WORK_DIR"
