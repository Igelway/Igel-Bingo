#!/bin/bash
set -e

echo "Setting up mod symlinks..."
find /data/mods -type l -delete 2>/dev/null || true
find /data/plugins -type l -delete 2>/dev/null || true

create_symlinks() {
  local source_dir="$1"
  local target_dir="$2"
  if [ ! -d "$source_dir" ]; then
    return
  fi
  mkdir -p "$target_dir"
  for file in "$source_dir"/*; do
    if [ -f "$file" ]; then
      local filename=$(basename "$file")
      if [[ "$filename" == *-dev.jar ]] || [[ "$filename" == *-sources.jar ]]; then
        echo "  Skipping $filename (dev/sources)"
        continue
      fi
      echo "  Symlinking $filename -> $target_dir/$filename"
      ln -sf "$file" "$target_dir/$filename"
    elif [ -d "$file" ]; then
      local dirname=$(basename "$file")
      create_symlinks "$file" "$target_dir/$dirname"
    fi
  done
}

create_symlinks "/opt/app-files/plugins" "/data/plugins"

echo "Starting Minecraft server..."

if [ -n "$PUID" ]; then export UID="$PUID"; fi
if [ -n "$PGID" ]; then export GID="$PGID"; fi

exec /start
