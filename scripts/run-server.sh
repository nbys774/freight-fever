#!/usr/bin/env bash
# Headless smoke test: starts a vanilla-flavoured dev server with the mod loaded.
set -e
cd "$(dirname "$0")/.."
if ! command -v java >/dev/null 2>&1; then
  echo "error: java (JDK 21) not found on PATH, set JAVA_HOME" >&2
  exit 1
fi
exec ./gradlew runServer --no-daemon
