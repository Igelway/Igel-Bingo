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

exec /start
