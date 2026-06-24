#!/bin/bash
set -e

echo "Setting up mod symlinks..."
find /data/mods -type l -delete 2>/dev/null || true
find /data/plugins -type l -delete 2>/dev/null || true

. /opt/shared-functions.sh

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

if [ -d "/opt/app-files/lobby-datapack" ]; then
  mkdir -p /data/world/datapacks
  cp -r /opt/app-files/lobby-datapack /data/world/datapacks/lobby
  echo "Lobby datapack installed."
fi

if [ -d "/opt/app-files/bingo-datapack" ]; then
  mkdir -p /data/world/datapacks
  cp -r /opt/app-files/bingo-datapack /data/world/datapacks/bingo_purpur
  echo "Bingo datapack installed."
fi

# Install bundled BAC datapack (same version as original server)
if [ -d "/opt/app-files/bac-datapack" ]; then
  mkdir -p /data/world/datapacks
  cp -r /opt/app-files/bac-datapack /data/world/datapacks/BAC
  echo "BAC datapack installed."
fi

# Download and filter BAC advancements at runtime
if [ -x "/opt/app-files/bac-filter.sh" ]; then
  echo "Running BAC filter to download and install advancements..."
  /opt/app-files/bac-filter.sh /data/world/datapacks/BAC_Filtered
fi

if [ -d "/opt/app-files/game-data" ]; then
  mkdir -p /data/plugins/BingoReloaded/data
  cp /opt/app-files/game-data/*.nbt /data/plugins/BingoReloaded/data/
  echo "Bingo data files installed."
fi

mkdir -p /data/plugins/BingoReloaded
echo "Installing BingoReloaded config with IgelBingo hooks..."
cat > /data/plugins/BingoReloaded/config.yml << 'CONFEOF'
version: 3.4.2
configuration: SINGULAR
defaultWorldName: world
language: de.yml
savePlayerStatistics: true
sendCommandAfterGameEnds: function bingo_setup:bingo_end/bingo_end
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
startingCountdownTime: 30
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
defaultWorlds:
- bingo_world
clearDefaultWorlds: true
customWorldGeneration: ''
CONFEOF

echo "Installing BingoReloaded sounds..."
cat > /data/plugins/BingoReloaded/sounds.yml << 'SOUNDSEOF'
# Each sound contains some properties that can be changed:
#   sound: Reference to the actual minecraft sound, as can be obtained using /playsound in-game.
#   volume: Loudness of the sound between 0.0 and 1.0, where 0.0 is silent and 1.0 is the loudest possible

go_up_wand_used:
  sound: "minecraft:entity.shulker.teleport"
  volume: 0.8

hotswap_task_added:
  sound: "minecraft:item.axe.wax_off"
  volume: 1.0

hotswap_task_expired:
  sound: "block.trial_spawner.spawn_item_begin"
  volume: 1.0
#minecraft:block.note_block.bit
countdown_tick_1:
  sound: ""
  volume: 0.0
#minecraft:block.note_block.pling
countdown_tick_2:
  sound: ""
  volume: 0.0

game_ended:
  sound: "minecraft:block.vault.open_shutter"
  volume: 1.0

game_won:
  sound: "minecraft:item.goat_horn.sound.1"
  volume: 0.6

deathmatch_initiated:
  sound: "minecraft:entity.happy_ghast.ambient"
  volume: 1.0

deathmatch_reveal:
  sound: "minecraft:entity.ghast.shoot"
  volume: 1.0

task_completed:
  sound: "minecraft:block.amethyst_cluster.step"
  volume: 1.0

start_countdown_finished_1:
  sound: "minecraft:item.goat_horn.sound.6"
  volume: 0.0

start_countdown_finished_2:
  sound: "minecraft:item.goat_horn.sound.5"
  volume: 0.0
SOUNDSEOF

if [ "$(id -u)" -eq 0 ]; then
  chown -R minecraft:minecraft /data 2>/dev/null || true
fi

echo "Starting Minecraft server..."

if [ -n "$PUID" ]; then export UID="$PUID"; fi
if [ -n "$PGID" ]; then export GID="$PGID"; fi

exec /start
