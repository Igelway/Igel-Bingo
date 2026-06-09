#!/bin/bash
set -e

PUID=${PUID:-1000}
PGID=${PGID:-1000}
RUN_AS_USER=""

if [ "$(id -u)" -eq 0 ]; then
  if ! getent group minecraft >/dev/null 2>&1; then
    groupadd -g "${PGID}" minecraft || true
  else
    groupmod -g "${PGID}" minecraft || true
  fi
  if ! id -u minecraft >/dev/null 2>&1; then
    useradd -u "${PUID}" -g "${PGID}" -m -s /bin/sh minecraft || true
  else
    usermod -u "${PUID}" -g "${PGID}" minecraft || true
  fi
  chown -R minecraft:minecraft /data /opt/app-files /opt/config-template 2>/dev/null || true
  RUN_AS_USER="minecraft"
else
  RUN_AS_USER=$(id -un)
fi

echo "Setting up configuration..."
if [ -d "/opt/config-template" ]; then
  mkdir -p /data
  cp -rn /opt/config-template/* /data/ 2>/dev/null || true
fi

# Apply runtime-configurable settings to velocity.toml
if [ -f "/data/velocity.toml" ]; then
  sed -i "s/^online-mode = .*/online-mode = ${VELOCITY_ONLINE_MODE:-true}/" /data/velocity.toml
  sed -i 's|^forwarding-secret-file = .*|forwarding-secret-file = "/run/secrets/forwarding_secret"|' /data/velocity.toml
  if [ -n "${VELOCITY_FORWARDING_MODE:-}" ]; then
    sed -i "s|^player-info-forwarding-mode = .*|player-info-forwarding-mode = \"${VELOCITY_FORWARDING_MODE}\"|" /data/velocity.toml
  fi
fi

echo "Setting up application file symlinks..."
find /data -type l -delete 2>/dev/null || true

create_symlinks() {
  local source_dir="$1"
  local target_dir="$2"
  if [ ! -d "$source_dir" ]; then
    return
  fi
  mkdir -p "$target_dir"
  for file in "$source_dir"/*; do
    if [ -f "$file" ]; then
      echo "  Symlinking $(basename "$file") -> $target_dir/"
      ln -sf "$file" "$target_dir/$(basename "$file")"
    elif [ -d "$file" ]; then
      local dirname=$(basename "$file")
      create_symlinks "$file" "$target_dir/$dirname"
    fi
  done
}

ln -sf /opt/app-files/velocity.jar /data/velocity.jar
create_symlinks "/opt/app-files/plugins" "/data/plugins"

echo "Starting Velocity..."
if [ -S /var/run/docker.sock ]; then
  DOCKER_GID=$(stat -c '%g' /var/run/docker.sock 2>/dev/null || echo "")
  if [ -n "$DOCKER_GID" ] && [ "$DOCKER_GID" != "0" ]; then
    groupmod -g "$DOCKER_GID" docker 2>/dev/null || groupadd -g "$DOCKER_GID" docker 2>/dev/null || true
  fi
  usermod -aG docker minecraft 2>/dev/null || true
fi
JAVA_BIN="${JAVA_HOME:-/opt/java/openjdk}/bin/java"
cd /data
if [ "$(id -u)" -eq 0 ]; then
  exec su -s /bin/sh minecraft -c "exec \"$JAVA_BIN\" -Xms1G -Xmx1G -jar /data/velocity.jar"
else
  exec "${JAVA_BIN}" -Xms1G -Xmx1G -jar /data/velocity.jar
fi
