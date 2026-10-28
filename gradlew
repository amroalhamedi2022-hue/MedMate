#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo 'Missing Gradle wrapper JAR. Download gradle-8.13-wrapper.jar from services.gradle.org and verify its SHA-256.' >&2
  exit 1
fi
if [ -n "${JAVA_HOME:-}" ]; then JAVA_EXE="$JAVA_HOME/bin/java"; else JAVA_EXE=java; fi
exec "$JAVA_EXE" -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
