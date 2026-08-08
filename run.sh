#!/usr/bin/env bash
# Starts the ContractGuard backend.
#   ./run.sh          -> dev profile, embedded H2, no MySQL needed
#   ./run.sh mysql    -> MySQL, using DB_USER / DB_PASSWORD from .env
set -euo pipefail

cd "$(dirname "$0")"

# --- Java 21 -----------------------------------------------------------------
# Spring Boot 3.2.5 supports Java 17-21. Newer JDKs (25, 26) will not start.
JDK21="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
if [ -d "$JDK21" ]; then
  export JAVA_HOME="$JDK21"
else
  echo "WARNING: JDK 21 not found at $JDK21"
  echo "         Install it with: brew install openjdk@21"
fi
echo "Java: $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"

# --- Environment -------------------------------------------------------------
if [ -f .env ]; then
  set -a; source .env; set +a
  echo "Loaded .env"
else
  echo "ERROR: no .env file. Run: cp .env.example .env"
  exit 1
fi

if [ -z "${LLM_API_KEY:-}" ] || [[ "${LLM_API_KEY}" == gsk_your* ]]; then
  echo "ERROR: LLM_API_KEY is not set in .env"
  exit 1
fi

# --- Mode --------------------------------------------------------------------
MODE="${1:-dev}"
case "$MODE" in
  mysql)
    echo "Starting with MySQL..."
    cd backend && mvn spring-boot:run
    ;;
  test)
    echo "Running tests (H2 in-memory, no MySQL needed)..."
    cd backend && mvn test
    ;;
  build)
    echo "Building jar..."
    cd backend && mvn clean package
    ;;
  dev)
    echo "Starting with embedded H2 (dev profile) - no MySQL required."
    cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
    ;;
  *)
    echo "Usage: ./run.sh [dev|mysql|test|build]"
    exit 1
    ;;
esac
