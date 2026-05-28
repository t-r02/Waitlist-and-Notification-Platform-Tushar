#!/usr/bin/env bash
# Bring up the full waitlist platform and wait until all three Spring services
# report healthy via /actuator/health.
set -euo pipefail

docker compose up -d --build

echo "Waiting for services to become healthy..."

for url in \
  http://localhost:8081/actuator/health \
  http://localhost:8082/actuator/health \
  http://localhost:8083/actuator/health
do
  echo -n "  $url ... "
  ready=false
  for i in {1..60}; do
    if curl -fs "$url" >/dev/null 2>&1; then
      ready=true
      break
    fi
    sleep 2
  done
  if $ready; then
    echo "UP"
  else
    echo "TIMEOUT"
    echo "ERROR: $url did not become healthy within 120 s" >&2
    exit 1
  fi
done

echo ""
echo "All services are up."
echo "  Ingestion : http://localhost:8081"
echo "  Admin     : http://localhost:8082"
echo "  Notify    : http://localhost:8083"
echo "  Mailpit   : http://localhost:8025"
