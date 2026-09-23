#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper (script-only). Downloads the Maven version pinned in
# .mvn/wrapper/maven-wrapper.properties on first use and runs it.
# Usage: ./mvnw spring-boot:run
# ----------------------------------------------------------------------------
set -e

BASEDIR=$(cd "$(dirname "$0")" && pwd)
PROPS="$BASEDIR/.mvn/wrapper/maven-wrapper.properties"

DEFAULT_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip"
DIST_URL=""
if [ -f "$PROPS" ]; then
  DIST_URL=$(grep '^distributionUrl=' "$PROPS" | cut -d'=' -f2- | tr -d '\r')
fi
[ -z "$DIST_URL" ] && DIST_URL="$DEFAULT_URL"
DIST_ZIP=$(basename "$DIST_URL")
DIST_NAME=${DIST_ZIP%-bin.zip}
WRAPPER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists"
MAVEN_HOME_DIR="$WRAPPER_HOME/$DIST_NAME"

if [ ! -x "$MAVEN_HOME_DIR/bin/mvn" ]; then
  echo "Downloading $DIST_URL"
  mkdir -p "$WRAPPER_HOME"
  TMP_DIR=$(mktemp -d "$WRAPPER_HOME/tmp.XXXXXX")
  trap 'rm -rf "$TMP_DIR"' EXIT
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$TMP_DIR/$DIST_ZIP" "$DIST_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$TMP_DIR/$DIST_ZIP" "$DIST_URL"
  else
    echo "Neither curl nor wget found; please install one of them." >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$TMP_DIR/$DIST_ZIP" -d "$TMP_DIR"
  else
    (cd "$TMP_DIR" && jar xf "$DIST_ZIP")
  fi
  rm -rf "$MAVEN_HOME_DIR"
  mv "$TMP_DIR/$DIST_NAME" "$MAVEN_HOME_DIR"
  chmod +x "$MAVEN_HOME_DIR/bin/mvn"
fi

cd "$BASEDIR"
exec "$MAVEN_HOME_DIR/bin/mvn" "$@"
