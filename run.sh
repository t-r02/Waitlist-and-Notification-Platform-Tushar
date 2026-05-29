#!/usr/bin/env bash
# Bring up the full waitlist platform and wait until all services report healthy.
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

echo -n "  http://localhost:8080 ... "
ready=false
for i in {1..60}; do
  if curl -fs "http://localhost:8080" >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done
if $ready; then
  echo "UP"
else
  echo "TIMEOUT"
  echo "ERROR: http://localhost:8080 did not become ready within 120 s" >&2
  exit 1
fi

echo ""
echo "All services are up."
echo ""
echo "  App pages:"
echo "    Sign up / Leaderboard  : http://localhost:8080"
echo "    Email verify (link)    : http://localhost:8080/verify?token=<token>"
echo "    Early Access check     : http://localhost:8080/access"
echo "    Referral profile       : http://localhost:8080/profile"
echo "    Admin dashboard        : http://localhost:8080/admin"
echo "    Fraud monitor          : http://localhost:8080/admin/fraud"
echo ""
echo "  APIs:"
echo "    Ingestion service      : http://localhost:8081"
echo "    Admin service          : http://localhost:8082"
echo "    Notification service   : http://localhost:8083"
echo ""
echo "  Dev tools:"
echo "    Mailpit (email UI)     : http://localhost:8025"
