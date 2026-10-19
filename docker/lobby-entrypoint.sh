#!/bin/sh
set -e

if [ -f "/run/secrets/forwarding_secret" ]; then
  SECRET=$(cat /run/secrets/forwarding_secret | tr -d '\n')
  export VELOCITY_SECRET="$SECRET"

  mkdir -p /data/config
  cat > /data/config/paper-global.yml << PAPEREOF
_version: 31
proxies:
  velocity:
    enabled: true
    online-mode: false
    secret: "${SECRET}"
PAPEREOF
  echo "Velocity forwarding configured for lobby."
else
  echo "WARNING: No forwarding secret found at /run/secrets/forwarding_secret"
fi

if [ -d "/opt/lobby-datapack" ]; then
  mkdir -p /data/world/datapacks
  cp -r /opt/lobby-datapack /data/world/datapacks/lobby
  echo "Lobby datapack installed."
fi

# MapModCompanion (separates Xaero's maps per world)
MAPMODCOMPANION_URL="https://github.com/turikhay/MapModCompanion/releases/download/v0.11.0-beta.2/MapModCompanion.jar"
MAPMODCOMPANION_SHA256="4af733839b51b8ad1c34d7d742ca2640f97e58b50c35993e73123be863bf5aff"
MAPMODCOMPANION_JAR="/data/plugins/MapModCompanion.jar"
mkdir -p /data/plugins
if ! echo "$MAPMODCOMPANION_SHA256  $MAPMODCOMPANION_JAR" | sha256sum -c - >/dev/null 2>&1; then
  echo "Installing MapModCompanion..."
  curl -fSL -o "$MAPMODCOMPANION_JAR" "$MAPMODCOMPANION_URL"
  echo "$MAPMODCOMPANION_SHA256  $MAPMODCOMPANION_JAR" | sha256sum -c -
fi

if [ "$(id -u)" -eq 0 ]; then
  chown -R minecraft:minecraft /data 2>/dev/null || true
fi

exec /start
