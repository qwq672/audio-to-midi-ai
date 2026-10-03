#!/bin/sh

#
# Gradle startup script for POSIX environments.
# Bootstrap the wrapper JAR if missing.
#

# Determine APP_HOME (directory of this script).
PRG="$0"
while [ -h "$PRG" ]; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.* -> \(.*\)$')
    case "$link" in
      /*) PRG="$link" ;;
      *)  PRG="$(dirname "$PRG")/$link" ;;
    esac
done
APP_HOME=$(cd "$(dirname "$PRG")" >/dev/null 2>&1 && pwd)

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Auto-download wrapper jar if missing.
if [ ! -f "$CLASSPATH" ]; then
    echo "Gradle wrapper jar missing. Downloading..."
    if command -v curl >/dev/null 2>&1; then
        curl -sSL -o "$CLASSPATH" https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar
    elif command -v wget >/dev/null 2>&1; then
        wget -qO "$CLASSPATH" https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar
    else
        echo "ERROR: cannot find curl/wget. Install Gradle manually and run 'gradle wrapper'." >&2
        exit 1
    fi
fi

# Determine JAVA_HOME / JAVACMD
if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ]; then
        echo "ERROR: JAVA_HOME points to invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1; then
        echo "ERROR: JAVA_HOME not set and 'java' not found in PATH." >&2
        exit 1
    fi
fi

# Pick up JVM opts from env, with safe defaults.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Execute Gradle wrapper main class with all args.
exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS \
    $JAVA_OPTS \
    $GRADLE_OPTS \
    "-Dorg.gradle.appname=$(basename "$0")" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
