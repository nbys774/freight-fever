#!/usr/bin/env bash
# Launches the Minecraft 1.21.1 dev client with Freight Fever loaded.
# Requires a display (this is a graphical game client).
set -e
cd "$(dirname "$0")/.."
if ! command -v java >/dev/null 2>&1; then
  echo "error: java (JDK 21) not found on PATH, set JAVA_HOME" >&2
  exit 1
fi
exec ./gradlew runClient --no-daemon
