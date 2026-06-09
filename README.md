# Igel-Bingo

Docker-based Bingo server system with Velocity proxy, NanoLimbo, and on-demand Purpur game servers.

## Components

| Container | Description |
|---|---|
| **velocity** | Velocity proxy + Igel-Bingo Velocity Plugin (24/7) |
| **limbo** | NanoLimbo — minimal idle server (24/7, ~300 MB RAM) |
| **lobby** | Purpur lobby — auto-starts when a player joins limbo, stops on idle timeout |
| **game** | Purpur game server — on-demand via `/ib start`, fresh world per game |

## Quick Start

```bash
# 1. First-time setup
just setup-env

# 2. Edit .env (at minimum set admins)
vim .env

# 3. Start
just up

# 4. Start a game (in Velocity console or as admin)
/ib start
```

## Commands (`/ib`)

| Command | Description |
|---|---|
| `/ib start [--clean]` | Start a game (--clean: delete old containers/volumes first) |
| `/ib prepare [seed]` | Pre-generate with Chunky in background |
| `/ib stop` | End the game |
| `/ib seed [seed]` | Set or view the seed for the next game |
| `/ib seed clear` | Clear the seed (random next start) |
| `/ib state` | Show current game state |
| `/ib cleanup` | Delete all game containers and data |

## Environment Variables

See `.env.example` for all options.

Key variables:
- `IGELBINGO_ADMINS` — Comma-separated admin usernames
- `IGELBINGO_GAME_MEMORY` — Game server RAM (default: 6G)
- `IGELBINGO_LOBBY_IDLE_TIMEOUT` — Seconds of inactivity before lobby stops (0 = disabled)
- `IGELBINGO_CHUNKY_PRELOAD` — Enable Chunky pre-generation

## Architecture

```
Player → Velocity (25565) → Limbo   (always running)
                          → Lobby   (auto-start on join, stops on idle)
                          → Game    (on-demand, /ib start)
```

- **Igel-Bingo Velocity Plugin**: Docker orchestration, player routing, admin commands
- **Igel-Bingo Game Plugin** (Paper): Countdown, starter kit (elytra + fireworks), game rules, world setup
- **BingoReloaded**: Core game logic (cards, teams, voting) — connected via config hooks

## BingoReloaded Integration

In `plugins/BingoReloaded/config.yml`:
```yaml
sendCommandBeforeGameStarts: "igelbingo start"
sendCommandAfterGameEnds: "igelbingo end"
startingCountdownTime: 0
```

## Development

```bash
# Build both plugins
just build

# Build Docker images locally
just docker-build

# Start with local images
IGELBINGO_VELOCITY_IMAGE=igel-bingo-velocity:local \
IGELBINGO_GAMESERVER_IMAGE=igel-bingo-gameserver:local \
just up
```

## License

MIT
