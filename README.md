# Igel-Bingo

Docker-basiertes Bingo-Server-System mit Velocity-Proxy, NanoLimbo und on-demand Purpur-Game-Servern.

## Komponenten

| Container | Beschreibung |
|---|---|
| **velocity** | Velocity-Proxy + IgelBingo Velocity Plugin (24/7) |
| **limbo** | NanoLimbo — minimaler Idle-Server (24/7) |
| **lobby** | Purpur-Lobby — startet automatisch bei Spieler-Join |
| **game** | Purpur-Game-Server — on-demand via `/ib start` |

## Quick Start

```bash
# 1. Ersteinrichtung
just setup-env

# 2. .env anpassen (mindestens Admins setzen)
vim .env

# 3. Starten
just up

# 4. Spiel starten (in der Velocity-Console oder als Admin)
/ib start
```

## Commands (`/ib`)

| Command | Beschreibung |
|---|---|
| `/ib start [--clean]` | Spiel starten |
| `/ib prepare [seed]` | Spiel mit Chunky-Pregeneration vorbereiten |
| `/ib stop` | Spiel beenden |
| `/ib seed [seed]` | Seed setzen/anzeigen |
| `/ib seed clear` | Seed löschen (Zufall) |
| `/ib state` | Status anzeigen |
| `/ib cleanup` | Alle Game-Container & Daten löschen |

## Env-Variablen

Siehe `.env.example` für alle verfügbaren Optionen.

Wichtige Variablen:
- `IGELBINGO_ADMINS` — Komma-getrennte Admin-Namen
- `IGELBINGO_GAME_MEMORY` — RAM für Game-Server (default: 6G)
- `IGELBINGO_LOBBY_IDLE_TIMEOUT` — Sekunden bis Lobby-Inaktivitäts-Stop (0=aus)
- `IGELBINGO_CHUNKY_PRELOAD` — Chunky-Vorgenerierung aktivieren

## Entwicklung

```bash
# Plugins bauen
just build

# Docker-Images lokal bauen
just docker-build

# Mit lokalen Images starten
IGELBINGO_VELOCITY_IMAGE=igel-bingo-velocity:local \
IGELBINGO_GAMESERVER_IMAGE=igel-bingo-gameserver:local \
just up
```

## Architektur

```
Spieler → Velocity (25565) → Limbo (immer)
                           → Lobby (auto-start bei Join)
                           → Game  (on-demand /ib start)
```

- **IgelBingo Velocity Plugin**: Docker-Orchestrierung, Player-Routing, Commands
- **IgelBingo Game Plugin** (Paper): Countdown, Starter-Kit, Gamerules, World-Init (ersetzt bingo_purpur Datapack)
- **BingoReloaded**: Spiel-Logik (Karten, Teams, Voting) — über Config-Hooks mit IgelBingo verbunden

## BingoReloaded-Integration

In `plugins/BingoReloaded/config.yml`:
```yaml
sendCommandBeforeGameStarts: "igelbingo start"
sendCommandAfterGameEnds: "igelbingo end"
startingCountdownTime: 0
```

## Lizenz

MIT
