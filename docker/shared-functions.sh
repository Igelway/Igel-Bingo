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
        continue
      fi
      ln -sf "$file" "$target_dir/$filename"
    elif [ -d "$file" ]; then
      create_symlinks "$file" "$target_dir/$(basename "$file")"
    fi
  done
}
