#!/bin/bash
set -e

# Download and filter BlazeandCave's Advancements Pack from Modrinth.
# Downloaded at runtime (not bundled) for license compliance.

BAC_DATAPACK_DIR="${1:-/data/world/datapacks/BAC_Filtered}"
WORK_DIR="/tmp/bac-filter-$$"

echo "=== BAC Filter ==="
echo "Downloading BlazeandCave's Advancements Pack from Modrinth..."

mkdir -p "$WORK_DIR"
mkdir -p "$BAC_DATAPACK_DIR"

BAC_URL="https://cdn.modrinth.com/data/VoVJ47kN/versions/Y2zZ5eSs/BlazeandCave%27s%20Advancements%20Pack%201.21.zip"

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

rm -rf "$BAC_DATAPACK_DIR"
mkdir -p "$BAC_DATAPACK_DIR"

cp "$WORK_DIR/extracted/pack.mcmeta" "$BAC_DATAPACK_DIR/" 2>/dev/null || true
cp "$WORK_DIR/extracted/pack.png" "$BAC_DATAPACK_DIR/" 2>/dev/null || true

# Keep only advancement files
if [ -d "$WORK_DIR/extracted/data/minecraft/advancement" ]; then
    mkdir -p "$BAC_DATAPACK_DIR/data/minecraft"
    cp -r "$WORK_DIR/extracted/data/minecraft/advancement" "$BAC_DATAPACK_DIR/data/minecraft/"
fi

if [ -d "$WORK_DIR/extracted/data/blazeandcave/advancement" ]; then
    mkdir -p "$BAC_DATAPACK_DIR/data/blazeandcave"
    cp -r "$WORK_DIR/extracted/data/blazeandcave/advancement" "$BAC_DATAPACK_DIR/data/blazeandcave/"
fi

if [ -d "$WORK_DIR/extracted/data/blazeandcave/tags" ]; then
    mkdir -p "$BAC_DATAPACK_DIR/data/blazeandcave"
    cp -r "$WORK_DIR/extracted/data/blazeandcave/tags" "$BAC_DATAPACK_DIR/data/blazeandcave/"
fi

if [ -d "$WORK_DIR/extracted/data/blazeandcave/function" ]; then
    mkdir -p "$BAC_DATAPACK_DIR/data/blazeandcave"
    cp -r "$WORK_DIR/extracted/data/blazeandcave/function" "$BAC_DATAPACK_DIR/data/blazeandcave/"
fi

if [ -d "$WORK_DIR/extracted/data/blazeandcave/predicate" ]; then
    mkdir -p "$BAC_DATAPACK_DIR/data/blazeandcave"
    cp -r "$WORK_DIR/extracted/data/blazeandcave/predicate" "$BAC_DATAPACK_DIR/data/blazeandcave/"
fi

if [ -d "$WORK_DIR/extracted/data/blazeandcave/msg" ]; then
    mkdir -p "$BAC_DATAPACK_DIR/data/blazeandcave"
    cp -r "$WORK_DIR/extracted/data/blazeandcave/msg" "$BAC_DATAPACK_DIR/data/blazeandcave/"
fi

ADV_COUNT=$(find "$BAC_DATAPACK_DIR" -name "*.json" | wc -l)
echo "BAC Filter complete: $ADV_COUNT advancement files installed to $BAC_DATAPACK_DIR"

rm -rf "$WORK_DIR"
