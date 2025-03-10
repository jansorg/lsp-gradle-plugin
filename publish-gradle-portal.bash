#!/usr/bin/env bash

if grep -q -E '\-SNAPSHOT' ./gradle.properties 2>&1; then
  echo >&2 "Cannot publish a SNAPSHOT version to the Gradle Plugin Portal. Terminating."
  exit 1
fi

./gradlew publishPlugins
