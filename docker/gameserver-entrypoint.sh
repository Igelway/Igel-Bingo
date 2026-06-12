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

if [ -f "/run/secrets/forwarding_secret" ]; then
  SECRET=$(cat /run/secrets/forwarding_secret | tr -d '\n')
  export VELOCITY_SECRET="$SECRET"
elif [ -n "${VELOCITY_SECRET:-}" ]; then
  SECRET="$VELOCITY_SECRET"
else
  echo "WARNING: No forwarding secret found"
fi

if [ -n "$SECRET" ]; then
  mkdir -p /data/config
  cat > /data/config/paper-global.yml << PAPEREOF
proxies:
  velocity:
    enabled: true
    online-mode: ${VELOCITY_ONLINE_MODE:-false}
    secret: "${SECRET}"
PAPEREOF
  echo "Velocity forwarding configured for Paper/Purpur."
fi

if [ -x "/opt/app-files/bac-filter.sh" ]; then
  echo "Running BAC filter to download and install advancements..."
  /opt/app-files/bac-filter.sh /data/world/datapacks/BAC_Filtered
fi

mkdir -p /data/plugins/BingoReloaded
if [ ! -f "/data/plugins/BingoReloaded/config.yml" ]; then
  echo "Installing default BingoReloaded config with IgelBingo hooks..."
  cat > /data/plugins/BingoReloaded/config.yml << 'CONFEOF'
version: 3.4.2
configuration: SINGULAR
defaultWorldName: world
language: de.yml
savePlayerStatistics: true
sendCommandAfterGameEnds: "igelbingo end"
sendCommandBeforeGameStarts: "igelbingo start"
playerGamemodeAfterGame: NONE
voteUsingCommandsOnly: false
selectTeamsUsingCommandsOnly: false
disableScoreboardSidebar: false
useIncludedResourcepack: false
useMapRenderer: false
showUniqueAdvancementItems: true
showUniqueStatisticItems: true
enableDebugLogging: false
disableCompanionMod: false
singlePlayerTeams: false
minimumPlayerCount: 0
playerWaitTime: 50
gameRestartTime: 30
useVoteSystem: false
preventPlayerGriefing: false
startingCountdownTime: 0
teleportMaxDistance: 0
playerTeleportStrategy: NONE
teleportBackAfterDeathMessage: false
teleportAfterDeathPeriod: 0
gracePeriod: 30
removeTaskItems: false
enableTeamChat: true
keepScoreboardVisible: true
showPlayerInScoreboard: true
disableAdvancements: false
disableStatistics: false
endGameWithoutTeams: false
allowViewingAllCards: true
savePlayerInformation: false
loadPlayerInformationStrategy: AFTER_LEAVING_WORLD
teleportToLobbyAfterGame:
  enabled: true
  delay: 10.0
  spread: 5
voteList:
  gamemodes:
  - lockout
  - hotswap
  - complete
  - regular
  kits:
  - hardcore
  - normal
  - overpowered
  - reloaded
  - custom_1
  - custom_2
  - custom_3
  - custom_4
  - custom_5
  cards:
  - default_card
  cardsizes:
  - '3'
  - '5'
hotswapMode:
  minimumExpirationTime: 5
  maximumExpirationTime: 30
  recoverTime: 0
  showExpirationAsDurability: true
GoUpWand:
  upDistance: 50
  downDistance: 25
  cooldown: 2.0
  platformLifetime: 10
CONFEOF
fi

if [ "$(id -u)" -eq 0 ]; then
  chown -R minecraft:minecraft /data 2>/dev/null || true
fi

echo "Starting Minecraft server..."

if [ -n "$PUID" ]; then export UID="$PUID"; fi
if [ -n "$PGID" ]; then export GID="$PGID"; fi

exec /start
