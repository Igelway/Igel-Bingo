#!/bin/bash
set -e

TARGET_DIR="$1"
mkdir -p "$TARGET_DIR"

download_with_checksum() {
  local url="$1"
  local sha256="$2"
  local filename=$(basename "${url%%\?*}")

  echo "Downloading $filename..."
  curl -fSL -o "$TARGET_DIR/$filename" "$url"

  if [ -n "$sha256" ]; then
    echo "Verifying $filename..."
    echo "$sha256  $TARGET_DIR/$filename" | sha256sum -c -
  fi
}

# BingoReloaded (Paper) - v3.4.2
download_with_checksum \
  "https://github.com/Steaf23/BingoReloaded/releases/download/v3.4.2/BingoReloaded-paper-3.4.2.jar" \
  ""

# PlaceholderAPI
download_with_checksum \
  "https://github.com/PlaceholderAPI/PlaceholderAPI/releases/download/2.12.2/PlaceholderAPI-2.12.2.jar" \
  ""

# LuckPerms for Bukkit (Modrinth CDN)
download_with_checksum \
  "https://cdn.modrinth.com/data/Vebnzrzj/versions/MBSY8toc/LuckPerms-Bukkit-5.5.53.jar" \
  ""

# Chunky (Hangar)
download_with_checksum \
  "https://hangar.papermc.io/api/v1/projects/pop4959/Chunky/versions/1.5.3/PAPER/download" \
  ""

# Spark (performance profiler)
download_with_checksum \
  "https://github.com/lucko/spark/releases/download/1.10.141/spark-bukkit-1.10.141.jar" \
  ""

echo "All plugins downloaded."
