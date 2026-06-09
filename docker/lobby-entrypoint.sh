#!/bin/bash
set -e

# Read Velocity forwarding secret
if [ -f /run/secrets/forwarding_secret ]; then
    export VELOCITY_SECRET=$(cat /run/secrets/forwarding_secret | tr -d '\n')
fi

# itzg image uses VELOCITY_SECRET to enable forwarding + modern mode
exec /start
