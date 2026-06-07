# Igel-Bingo — Justfile
#
# Usage:
#   just up              Start all services
#   just up playit       Start with playit.gg tunnel
#   just down            Stop all services
#   just build           Build both plugins
#   just docker-build    Build both Docker images locally
#   just logs            Follow all container logs
#   just console name    Attach to container console (velocity/lobby/limbo/game)
#   just pull            Pull latest Docker images
#   just setup-env       Create .env and secrets for first-time setup

default:
    @just --list

# ── Setup ──────────────────────────────────────────────

setup-env:
    @echo "Setting up Igel-Bingo environment..."
    @test -f .env || cp .env.example .env
    @test -f .forwarding.secret || openssl rand -hex 16 > .forwarding.secret
    @test -f .playit.secret || touch .playit.secret
    @mkdir -p data/velocity data/limbo data/lobby
    @echo "Setup complete. Edit .env to configure."

# ── Build ──────────────────────────────────────────────

build:
    @echo "Building igelbingo-velocity..."
    cd igelbingo-velocity && gradle build --no-daemon
    @echo "Building igelbingo-game..."
    cd igelbingo-game && gradle build --no-daemon

docker-build-velocity: build
    @echo "Building velocity Docker image..."
    docker build -t igel-bingo-velocity:local -f docker/Dockerfile.velocity .

docker-build-gameserver: build
    @echo "Building gameserver Docker image..."
    docker build -t igel-bingo-gameserver:local -f docker/Dockerfile.gameserver .

docker-build: docker-build-velocity docker-build-gameserver
    @echo "All Docker images built."

# ── Compose ─────────────────────────────────────────────

up _profile="":
    @echo "Starting Igel-Bingo..."
    @if [ "{{_profile}}" = "playit" ]; then \
        COMPOSE_PROFILES=playit docker compose up -d --remove-orphans; \
    else \
        docker compose up -d --remove-orphans; \
    fi
    @echo "All services started."

down:
    @echo "Stopping Igel-Bingo..."
    docker compose down --remove-orphans

restart _profile="":
    @just down
    @just up {{_profile}}

# ── Management ──────────────────────────────────────────

logs:
    docker compose logs -f

console server="velocity":
    docker attach igelbingo-{{server}}

pull:
    docker compose pull

ps:
    docker compose ps

# ── Game ────────────────────────────────────────────────

cmd command:
    @echo "Sending command to Velocity..."
    docker compose exec velocity rcon-cli "{{command}}" 2>/dev/null || true
