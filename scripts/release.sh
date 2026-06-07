#!/bin/bash
set -e

VERSION="$1"

if [ -z "$VERSION" ]; then
    echo "Usage: ./scripts/release.sh <version>"
    echo "Example: ./scripts/release.sh 1.0.1"
    exit 1
fi

echo "=== Releasing Igel-Bingo v$VERSION ==="

sed -i "s/^version = \".*\"/version = \"$VERSION\"/" igelbingo-velocity/build.gradle.kts
sed -i "s/^version = \".*\"/version = \"$VERSION\"/" igelbingo-game/build.gradle.kts
sed -i 's/"version": "[^"]*"/"version": "'"$VERSION"'"/' igelbingo-velocity/src/main/resources/velocity-plugin.json
sed -i "s/^version: .*/version: $VERSION/" igelbingo-game/src/main/resources/plugin.yml

git add \
    igelbingo-velocity/build.gradle.kts \
    igelbingo-game/build.gradle.kts \
    igelbingo-velocity/src/main/resources/velocity-plugin.json \
    igelbingo-game/src/main/resources/plugin.yml

git commit -m "chore: bump version to $VERSION" || true
git tag "v$VERSION"

echo ""
echo "=== Version $VERSION tagged ==="
echo "Push:  git push origin dev && git push origin v$VERSION"
echo "CI/CD builds plugins + Docker images → GHCR automatically."
