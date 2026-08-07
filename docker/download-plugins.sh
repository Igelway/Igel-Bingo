#!/bin/bash
set -e

TARGET_DIR="$1"
mkdir -p "$TARGET_DIR"

download_with_checksum() {
  local url="$1"
  local sha256="$2"
  local filename="$3"

  if [ -z "$filename" ]; then
    filename=$(basename "${url%%\?*}")
  fi

  echo "Downloading $filename..."
  curl -fSL -o "$TARGET_DIR/$filename" "$url"

  if [ -n "$sha256" ]; then
    echo "Verifying $filename..."
    echo "$sha256  $TARGET_DIR/$filename" | sha256sum -c -
  fi
}

# BingoReloaded (Paper) - v3.5.2 for Minecraft 26.1.2
download_with_checksum \
  "https://github.com/Steaf23/BingoReloaded/releases/download/v3.5.2/BingoReloaded-paper-3.5.2-26.1.2.jar" \
  ""

# PlaceholderAPI
download_with_checksum \
  "https://github.com/PlaceholderAPI/PlaceholderAPI/releases/download/2.12.3/PlaceholderAPI-2.12.3.jar" \
  ""

# LuckPerms for Bukkit
download_with_checksum \
  "https://cdn.modrinth.com/data/Vebnzrzj/versions/MBSY8toc/LuckPerms-Bukkit-5.5.53.jar" \
  ""

# PacketEvents
download_with_checksum \
  "https://github.com/retrooper/packetevents/releases/download/v2.13.0/packetevents-spigot-2.13.0.jar" \
  ""

# Chunky
download_with_checksum \
  "https://hangar.papermc.io/api/v1/projects/pop4959/Chunky/versions/1.5.3/PAPER/download" \
  "" \
  "Chunky-Bukkit-1.5.3.jar"

echo "All plugins downloaded."
