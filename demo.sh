#!/usr/bin/env bash
# End-to-end smoke test — runs blind on a fresh stack.
# Requires: curl, jq
# Exits non-zero on the first failure.
set -euo pipefail

for tool in curl jq; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "ERROR: '$tool' is required but not found on PATH" >&2; exit 1
  }
done

INGESTION="http://localhost:8081"
ADMIN="http://localhost:8082"
MAILPIT="http://localhost:8025"

EMAIL="demo.$(date +%s)@example.com"

log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { log "ERROR: $*"; exit 1; }

log "Run email: $EMAIL"

# ── 1. Sign up ────────────────────────────────────────────────────────────────
log "1/6  Sign up"
SIGNUP=$(curl -sf -X POST "$INGESTION/api/public/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"name\":\"Demo User\"}")
log "     $(echo "$SIGNUP" | jq -c '{duplicate,verified}')"

echo "$SIGNUP" | jq -e '.verified == false' >/dev/null \
  || die "new signup should be unverified"
echo "$SIGNUP" | jq -e '.referralCode == null' >/dev/null \
  || die "referral code must be hidden before verification"

# ── 2. Extract verification token from Mailpit ────────────────────────────────
log "2/6  Waiting for verification email (up to 20 s)"
VTOKEN=""
for i in $(seq 1 10); do
  VTOKEN=$(curl -sf "$MAILPIT/api/v1/messages?limit=20" | \
    jq -r --arg e "$EMAIL" '
      .messages[]
      | select(.To != null and (.To | any(.Address == $e)))
      | select(.Subject | test("Verify"; "i"))
      | .Snippet
    ' | grep -oP 'token=\K[a-f0-9]{64}' | head -1)
  if [[ -n "$VTOKEN" ]]; then
    log "     Token captured (attempt $i)"
    break
  fi
  log "     Attempt $i: email not yet in Mailpit — waiting 2 s"
  sleep 2
done
[[ -n "$VTOKEN" ]] || die "verification email never arrived after 20 s"

# ── 3. Verify email ───────────────────────────────────────────────────────────
log "3/6  Verify email"
VERIFY=$(curl -sf -X POST "$INGESTION/api/public/verify?token=$VTOKEN")
REF_CODE=$(echo "$VERIFY" | jq -r '.referralCode')
log "     Referral code: $REF_CODE"
[[ -n "$REF_CODE" && "$REF_CODE" != "null" ]] \
  || die "referral code should be returned after verification"

# ── 4. Admin login ────────────────────────────────────────────────────────────
log "4/6  Admin login"
TOKEN=$(curl -sf -X POST "$ADMIN/api/admin/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')
[[ -n "$TOKEN" && "$TOKEN" != "null" ]] || die "admin login failed"
log "     JWT acquired"

# ── 5. Poll admin entries until the verified signup propagates ────────────────
log "5/6  Waiting for entry to appear in admin dashboard (up to 30 s)"
ENTRY_ID=""
for i in $(seq 1 15); do
  ENTRY_ID=$(curl -sf "$ADMIN/api/admin/entries" \
    -H "Authorization: Bearer $TOKEN" | \
    jq -r --arg e "$EMAIL" '.[] | select(.email == $e) | .id | tostring')
  if [[ -n "$ENTRY_ID" && "$ENTRY_ID" != "null" ]]; then
    log "     Entry id=$ENTRY_ID (attempt $i)"
    break
  fi
  log "     Attempt $i: not yet visible — waiting 2 s"
  sleep 2
done
[[ -n "$ENTRY_ID" && "$ENTRY_ID" != "null" ]] \
  || die "entry never appeared in admin service after 30 s"

# ── 5b. Approve ───────────────────────────────────────────────────────────────
HTTP=$(curl -s -o /dev/null -w '%{http_code}' \
  -X PATCH "$ADMIN/api/admin/entries/$ENTRY_ID?status=APPROVED" \
  -H "Authorization: Bearer $TOKEN")
[[ "$HTTP" == "200" ]] || die "PATCH returned HTTP $HTTP"
log "     Approved"

# ── 6. Confirm three emails arrived ──────────────────────────────────────────
log "6/6  Polling Mailpit for 3 emails (verify + welcome + status) up to 30 s"
COUNT=0
for i in $(seq 1 15); do
  COUNT=$(curl -sf "$MAILPIT/api/v1/messages?limit=50" | \
    jq --arg e "$EMAIL" \
       '[.messages[] | select(.To != null and (.To | any(.Address == $e)))] | length')
  log "     Attempt $i: $COUNT email(s) received"
  [[ "$COUNT" -ge 3 ]] && break
  sleep 2
done

if [[ "$COUNT" -ge 3 ]]; then
  log ""
  log "OK — $COUNT emails confirmed for $EMAIL"
  log "  1. Verification link        (ingestion-service, on signup)"
  log "  2. Welcome + referral code  (notification-service, after email verified)"
  log "  3. Status update            (notification-service, on APPROVED)"
  exit 0
else
  die "timed out — only $COUNT email(s) received (expected 3)"
fi
