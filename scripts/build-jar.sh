#!/usr/bin/env bash
# Builds the release jar. Optional first argument: a mods directory to copy the jar into.
set -e
cd "$(dirname "$0")/.."
if ! command -v java >/dev/null 2>&1; then
  echo "error: java (JDK 21) not found on PATH, set JAVA_HOME" >&2
  exit 1
fi
./gradlew build --no-daemon
JAR=$(ls -t build/libs/freightfever-*.jar | grep -v sources | head -1)
echo "built: $JAR"
if [ -n "$1" ]; then
  cp "$JAR" "$1/"
  echo "copied to: $1"
fi
