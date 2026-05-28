#!/usr/bin/env bash
# End-to-end smoke test — runs blind on a fresh checkout.
# Requires: curl, jq
# Exits non-zero on any failure.
set -euo pipefail

# ── Dependency check ─────────────────────────────────────────────────────────
for tool in curl jq; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "ERROR: '$tool' is required but not found on PATH" >&2; exit 1
  }
done

INGESTION="http://localhost:8081"
ADMIN="http://localhost:8082"
MAILPIT="http://localhost:8025"

# Unique email per run so reruns never clash with existing data
EMAIL="demo.$(date +%s)@example.com"

log() { echo "[$(date +%H:%M:%S)] $*"; }

log "Test email: $EMAIL"

# ── 1. Sign up ───────────────────────────────────────────────────────────────
log "Step 1 — POST $INGESTION/api/public/signup"
SIGNUP=$(curl -sf -X POST "$INGESTION/api/public/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"name\":\"Demo User\"}")
log "  Response: $SIGNUP"

# ── 2. Admin login → token ───────────────────────────────────────────────────
log "Step 2 — POST $ADMIN/api/admin/auth/login"
LOGIN=$(curl -sf -X POST "$ADMIN/api/admin/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
TOKEN=$(echo "$LOGIN" | jq -r '.token')
[[ -z "$TOKEN" || "$TOKEN" == "null" ]] && {
  log "ERROR: login failed — response: $LOGIN"; exit 1
}
log "  JWT acquired (${#TOKEN} chars)"

# ── 3. Poll admin entries until our signup propagates via Kafka ───────────────
log "Step 3 — Polling $ADMIN/api/admin/entries for $EMAIL (up to 30 s)"
ENTRY_ID=""
for i in $(seq 1 15); do
  ENTRIES=$(curl -sf "$ADMIN/api/admin/entries" \
    -H "Authorization: Bearer $TOKEN")
  ENTRY_ID=$(echo "$ENTRIES" | jq -r --arg e "$EMAIL" \
    '.[] | select(.email == $e) | .id | tostring')
  if [[ -n "$ENTRY_ID" && "$ENTRY_ID" != "null" ]]; then
    log "  Entry id=$ENTRY_ID found (attempt $i)"
    break
  fi
  log "  Attempt $i: entry not yet visible — waiting 2 s"
  sleep 2
done
[[ -z "$ENTRY_ID" || "$ENTRY_ID" == "null" ]] && {
  log "ERROR: entry never appeared in admin-service after 30 s"; exit 1
}

# ── 4. Approve the entry ─────────────────────────────────────────────────────
log "Step 4 — PATCH $ADMIN/api/admin/entries/$ENTRY_ID?status=APPROVED"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X PATCH "$ADMIN/api/admin/entries/$ENTRY_ID?status=APPROVED" \
  -H "Authorization: Bearer $TOKEN")
[[ "$HTTP_CODE" == "200" ]] || {
  log "ERROR: PATCH returned HTTP $HTTP_CODE"; exit 1
}
log "  Approved (HTTP $HTTP_CODE)"

# ── 5. Poll Mailpit for exactly 2 emails to $EMAIL ───────────────────────────
log "Step 5 — Polling $MAILPIT for 2 emails to $EMAIL (up to 30 s)"
COUNT=0
for i in $(seq 1 15); do
  # Filter messages by To address using jq (avoids URL-encoding the @ in curl)
  MESSAGES=$(curl -sf "$MAILPIT/api/v1/messages?limit=50")
  COUNT=$(echo "$MESSAGES" | jq --arg e "$EMAIL" \
    '[.messages[] | select(.To != null and (.To | any(.Address == $e)))] | length')
  log "  Attempt $i: $COUNT email(s) received"
  if [[ "$COUNT" -ge 2 ]]; then
    break
  fi
  sleep 2
done

if [[ "$COUNT" -ge 2 ]]; then
  log ""
  log "OK — $COUNT emails received for $EMAIL"
  log "  • Confirmation email (on signup)"
  log "  • Status-update email (on APPROVED)"
  exit 0
else
  log "ERROR: timed out — only $COUNT email(s) received for $EMAIL after 30 s"
  exit 1
fi
