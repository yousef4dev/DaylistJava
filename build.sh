#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVA_HOME=${JAVA_HOME:-"$ROOT/.tools/runtime/usr/lib/jvm/java-21-openjdk-amd64"}

if [ ! -x "$JAVA_HOME/bin/javac" ] || [ ! -x "$JAVA_HOME/bin/jpackage" ]; then
  printf '%s\n' "A Java 21 JDK with javac and jpackage is required." >&2
  printf '%s\n' "Install openjdk-21-jdk or set JAVA_HOME to an existing JDK." >&2
  exit 1
fi

rm -rf "$ROOT/build/classes" "$ROOT/build/input" "$ROOT/build/package/Daylist"
mkdir -p "$ROOT/build/classes" "$ROOT/build/input" "$ROOT/build/package"

"$JAVA_HOME/bin/javac" --release 21 -d "$ROOT/build/classes" "$ROOT/src/daylist/DaylistApp.java"
"$JAVA_HOME/bin/jar" --create --file "$ROOT/build/input/Daylist.jar" \
  --main-class daylist.DaylistApp -C "$ROOT/build/classes" .
"$JAVA_HOME/bin/jpackage" \
  --type app-image \
  --name Daylist \
  --app-version 1.0.0 \
  --vendor Daylist \
  --input "$ROOT/build/input" \
  --main-jar Daylist.jar \
  --main-class daylist.DaylistApp \
  --dest "$ROOT/build/package"

printf 'Built desktop app: %s\n' "$ROOT/build/package/Daylist/bin/Daylist"