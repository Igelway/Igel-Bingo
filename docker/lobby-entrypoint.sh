#!/bin/sh
set -e

if [ -f "/run/secrets/forwarding_secret" ]; then
  SECRET=$(cat /run/secrets/forwarding_secret | tr -d '\n')
  export VELOCITY_SECRET="$SECRET"
  export CFG_proxies.velocity.enabled=true
  export CFG_proxies.velocity.online-mode=false
  export CFG_proxies.velocity.secret="$SECRET"
  echo "Velocity forwarding configured for lobby."
else
  echo "WARNING: No forwarding secret found at /run/secrets/forwarding_secret"
fi

exec /start
