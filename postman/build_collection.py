import json

def make_item(name, method, url, headers=None, body=None, pre_lines=None, test_lines=None):
    header_list = []
    if headers:
        header_list = [{"key": k, "value": v} for k, v in headers.items()]
    r = {"method": method, "url": {"raw": url, "host": [url]}, "header": header_list}
    if body is not None:
        r["body"] = {"mode": "raw", "raw": body, "options": {"raw": {"language": "json"}}}
    obj = {"name": name, "request": r}
    events = []
    if pre_lines:
        events.append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": pre_lines}})
    if test_lines:
        events.append({"listen": "test", "script": {"type": "text/javascript", "exec": test_lines}})
    if events:
        obj["event"] = events
    return obj

def folder(name, items):
    return {"name": name, "item": items}

J = "application/json"
HDRS_JSON      = {"Content-Type": J}
HDRS_AUTH      = {"Content-Type": J, "Authorization": "Bearer {{token}}"}
HDRS_AUTH_ONLY = {"Authorization": "Bearer {{token}}"}
# Bypass per-IP rate limit (10 req/min) for tests that come after the first 10 /api/public/** calls.
# The RateLimitInterceptor reads X-Forwarded-For first, so a unique IP = a fresh bucket.
HDRS_RL1       = {"Content-Type": J, "X-Forwarded-For": "10.0.1.1"}
HDRS_RL2       = {"Content-Type": J, "X-Forwarded-For": "10.0.1.2"}
HDRS_RL3       = {"X-Forwarded-For": "10.0.1.3"}
# Fixture signup IPs: bypass main-IP per-IP bucket; keep bulk entries isolated from real test emails
HDRS_FX1       = {"Content-Type": J, "X-Forwarded-For": "10.0.2.1"}
HDRS_FX2       = {"Content-Type": J, "X-Forwarded-For": "10.0.2.2"}
HDRS_FX3       = {"Content-Type": J, "X-Forwarded-For": "10.0.2.3"}

SETUP_PRE = [
    "const ts = Date.now();",
    "pm.environment.set('runEmail',    'e2e.' + ts + '@postman.test');",
    "pm.environment.set('runRefEmail', 'ref.' + ts + '@postman.test');",
    "pm.environment.set('bulkEmail1',  'bulk.' + ts + '.1@fixture.test');",
    "pm.environment.set('bulkEmail2',  'bulk.' + ts + '.2@fixture.test');",
    "pm.environment.set('bulkEmail3',  'bulk.' + ts + '.3@fixture.test');",
    "['token','refCode','entryId','refEntryId','bulkId1','bulkId2','bulkId3'].forEach(k => pm.environment.set(k, ''));",
    "console.log('runEmail:', pm.environment.get('runEmail'));",
]
SETUP_TESTS = [
    "pm.test('ingestion-service UP', () => pm.response.to.have.status(200));",
    "pm.test('status UP', () => pm.expect(pm.response.json().status).to.eql('UP'));",
]
SIGNUP_TESTS = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "pm.test('duplicate false', () => pm.expect(pm.response.json().duplicate).to.be.false);",
    "pm.test('referralCode 8 chars — saves refCode', () => {",
    "  const b = pm.response.json();",
    "  pm.expect(b.referralCode).to.be.a('string').with.lengthOf(8);",
    "  pm.environment.set('refCode', b.referralCode);",
    "});",
]
SIGNUP_REF_TESTS = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "pm.test('referral signup not duplicate', () => pm.expect(pm.response.json().duplicate).to.be.false);",
]
LB_TESTS = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "pm.test('array response', () => pm.expect(pm.response.json()).to.be.an('array'));",
]
DUP_TESTS = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "pm.test('duplicate true', () => pm.expect(pm.response.json().duplicate).to.be.true);",
    "pm.test('same referralCode', () => pm.expect(pm.response.json().referralCode).to.eql(pm.environment.get('refCode')));",
]
UPPER_PRE = ["pm.environment.set('upperEmail', pm.environment.get('runEmail').toUpperCase());"]
UPPER_TESTS = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "pm.test('case-insensitive dedup', () => pm.expect(pm.response.json().duplicate).to.be.true);",
]
V400 = ["pm.test('Status 400', () => pm.response.to.have.status(400));",
        "pm.test('errors field present', () => pm.expect(pm.response.json()).to.have.property('errors'));"]
MALFORMED_TESTS = [
    "// BUG: missing HttpMessageNotReadableException handler — returns 500 instead of 400",
    "pm.test('Malformed JSON rejected (bug: 500, should be 400)', () => pm.expect([400,500]).to.include(pm.response.code));",
    "if (pm.response.code === 500) console.warn('BUG #1: malformed JSON returns 500');",
]
HONEYPOT_TESTS = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "pm.test('code 00000000', () => pm.expect(pm.response.json().referralCode).to.eql('00000000'));",
    "pm.test('duplicate true', () => pm.expect(pm.response.json().duplicate).to.be.true);",
]
LB_BOGUS = [
    "pm.test('Status 400', () => pm.response.to.have.status(400));",
    "pm.test('error message present', () => pm.expect(pm.response.json()).to.have.property('message'));",
]
LOGIN_TESTS = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "pm.test('token present — saves token', () => {",
    "  const b = pm.response.json();",
    "  pm.expect(b.token).to.be.a('string').with.lengthOf.above(20);",
    "  pm.environment.set('token', b.token);",
    "});",
]
T401 = lambda lbl: ["pm.test('Status 401 - %s', () => pm.response.to.have.status(401));" % lbl]
LIST_TESTS = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "pm.test('array', () => pm.expect(pm.response.json()).to.be.an('array'));",
    "const entries = pm.response.json();",
    "const runEmail = pm.environment.get('runEmail');",
    "const entry = entries.find(e => e.email === runEmail);",
    "const refEntry = entries.find(e => e.email === pm.environment.get('runRefEmail'));",
    "pm.test('runEmail projected to admin', () => pm.expect(entry).to.not.be.undefined);",
    "if (entry)    pm.environment.set('entryId',    String(entry.id));",
    "if (refEntry) pm.environment.set('refEntryId', String(refEntry.id));",
    "console.log('entryId:', pm.environment.get('entryId'), ' refEntryId:', pm.environment.get('refEntryId'));",
]
FILTER_TESTS = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "pm.test('all PENDING', () => pm.response.json().forEach(e => pm.expect(e.status).to.eql('PENDING')));",
]
BOGUS_STATUS = [
    "pm.test('Status 400', () => pm.response.to.have.status(400));",
    "pm.test('mentions BOGUS', () => pm.expect(pm.response.json().message).to.include('BOGUS'));",
]
GET_PENDING_TESTS = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "const entries = pm.response.json();",
    "// Locate fixture bulk entries by email (set in SETUP_PRE); guaranteed PENDING on every run.",
    "const b1 = entries.find(x => x.email === pm.environment.get('bulkEmail1'));",
    "const b2 = entries.find(x => x.email === pm.environment.get('bulkEmail2'));",
    "const b3 = entries.find(x => x.email === pm.environment.get('bulkEmail3'));",
    "pm.test('has fixture PENDING entries', () => { pm.expect(b1).to.not.be.undefined; pm.expect(b2).to.not.be.undefined; pm.expect(b3).to.not.be.undefined; });",
    "if(b1) pm.environment.set('bulkId1', String(b1.id));",
    "if(b2) pm.environment.set('bulkId2', String(b2.id));",
    "if(b3) pm.environment.set('bulkId3', String(b3.id));",
    "console.log('bulkIds:', pm.environment.get('bulkId1'), pm.environment.get('bulkId2'), pm.environment.get('bulkId3'));",
]
BULK_OK = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "pm.test('successCount 2', () => pm.expect(pm.response.json().successCount).to.eql(2));",
    "pm.test('failures empty', () => pm.expect(pm.response.json().failures).to.eql([]));",
]
BULK_207 = [
    "pm.test('Status 207', () => pm.response.to.have.status(207));",
    "pm.test('successCount 1', () => pm.expect(pm.response.json().successCount).to.eql(1));",
    "pm.test('failures non-empty', () => pm.expect(pm.response.json().failures.length).to.be.above(0));",
]
B400 = ["pm.test('Status 400', () => pm.response.to.have.status(400));",
        "pm.test('errors', () => pm.expect(pm.response.json()).to.have.property('errors'));"]
MAILPIT_TESTS = [
    "pm.test('Status 200', () => pm.response.to.have.status(200));",
    "const msgs = (pm.response.json().messages || []);",
    "const runEmail = pm.environment.get('runEmail');",
    "const matching = msgs.filter(m => (m.To||[]).some(t=>(t.Address||'').toLowerCase()===runEmail.toLowerCase()));",
    "pm.test('at least 2 emails for runEmail', () => pm.expect(matching.length).to.be.at.least(2));",
    "const subjects = matching.map(m => m.Subject);",
    "pm.test('Welcome email sent', () => pm.expect(subjects.some(s=>s&&(s.includes('Welcome')||s.includes('Waitlist')))).to.be.true);",
    "pm.test('Status update sent', () => pm.expect(subjects.some(s=>s&&s.includes('Status'))).to.be.true);",
    "console.log('Emails:', subjects);",
]

A121 = "A" * 121

collection = {
    "info": {
        "name": "Waitlist Platform",
        "_postman_id": "waitlist-platform-v2",
        "description": "Full E2E test collection. Run after: docker compose up -d. Requires services on :8081 :8082 :8025.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    },
    "variable": [
        {"key": k, "value": "", "type": "string"}
        for k in ["runEmail","runRefEmail","upperEmail","bulkEmail1","bulkEmail2","bulkEmail3",
                  "token","refCode","entryId","refEntryId","bulkId1","bulkId2","bulkId3"]
    ],
    "item": [
        folder("00 Setup", [
            make_item("Health check (initialises run variables)", "GET",
                      "{{baseIngestion}}/actuator/health",
                      pre_lines=SETUP_PRE, test_lines=SETUP_TESTS),
            # Three fixture signups for bulk tests — each uses a unique spoofed IP so they
            # never consume the main-IP rate-limit bucket (10 req/min).
            make_item("Fixture signup bulk-1 (for bulk tests)", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_FX1,
                      body='{"email":"{{bulkEmail1}}","name":"Bulk Fixture 1"}',
                      test_lines=["pm.test('bulk-1 registered', () => pm.expect([200,200]).to.include(pm.response.code));"]),
            make_item("Fixture signup bulk-2 (for bulk tests)", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_FX2,
                      body='{"email":"{{bulkEmail2}}","name":"Bulk Fixture 2"}',
                      test_lines=["pm.test('bulk-2 registered', () => pm.expect([200,200]).to.include(pm.response.code));"]),
            make_item("Fixture signup bulk-3 (for bulk tests)", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_FX3,
                      body='{"email":"{{bulkEmail3}}","name":"Bulk Fixture 3"}',
                      test_lines=["pm.test('bulk-3 registered', () => pm.expect([200,200]).to.include(pm.response.code));"]),
        ]),
        folder("Public — happy path", [
            make_item("Signup (saves refCode)", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_JSON,
                      body='{"email":"{{runEmail}}","name":"E2E Alice","company":"Postman Co"}',
                      test_lines=SIGNUP_TESTS),
            make_item("Signup via referral (EMAIL_B)", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_JSON,
                      body='{"email":"{{runRefEmail}}","name":"E2E Bob","referralCode":"{{refCode}}"}',
                      test_lines=SIGNUP_REF_TESTS),
            make_item("Leaderboard window=all", "GET",
                      "{{baseIngestion}}/api/public/leaderboard?window=all", test_lines=LB_TESTS),
            make_item("Leaderboard window=week", "GET",
                      "{{baseIngestion}}/api/public/leaderboard?window=week", test_lines=LB_TESTS),
        ]),
        folder("Public — negative", [
            make_item("Duplicate signup → duplicate=true", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_JSON,
                      body='{"email":"{{runEmail}}","name":"Dup"}', test_lines=DUP_TESTS),
            make_item("Uppercase dedup → duplicate=true", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_JSON,
                      body='{"email":"{{upperEmail}}"}', pre_lines=UPPER_PRE, test_lines=UPPER_TESTS),
            make_item("Invalid email → 400", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_JSON,
                      body='{"email":"not-an-email"}', test_lines=V400),
            make_item("Missing email → 400", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_JSON,
                      body='{"name":"NoEmail"}', test_lines=V400),
            make_item("Malformed JSON → 400 (bug: 500)", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_JSON,
                      body="NOT JSON AT ALL", test_lines=MALFORMED_TESTS),
            make_item("Name > 120 chars → 400", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_JSON,
                      body='{"email":"long@test.com","name":"' + A121 + '"}',
                      test_lines=["pm.test('Status 400', () => pm.response.to.have.status(400));"]),
            make_item("Bad referralCode pattern → 400", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_RL1,
                      body='{"email":"bad@test.com","referralCode":"abc"}',
                      test_lines=["pm.test('Status 400', () => pm.response.to.have.status(400));"]),
            make_item("Honeypot → 200 + code 00000000", "POST",
                      "{{baseIngestion}}/api/public/signup", headers=HDRS_RL2,
                      body='{"email":"bot@test.com","name":"Bot","website":"http://spam.com"}',
                      test_lines=HONEYPOT_TESTS),
            make_item("Leaderboard bogus window → 400", "GET",
                      "{{baseIngestion}}/api/public/leaderboard?window=bogus", headers=HDRS_RL3,
                      test_lines=LB_BOGUS),
        ]),
        folder("Admin — auth", [
            make_item("Login success (saves token)", "POST",
                      "{{baseAdmin}}/api/admin/auth/login", headers=HDRS_JSON,
                      body='{"username":"admin","password":"admin123"}', test_lines=LOGIN_TESTS),
            make_item("Login wrong password → 401", "POST",
                      "{{baseAdmin}}/api/admin/auth/login", headers=HDRS_JSON,
                      body='{"username":"admin","password":"wrongpass"}', test_lines=T401("wrong password")),
            make_item("Login unknown user → 401", "POST",
                      "{{baseAdmin}}/api/admin/auth/login", headers=HDRS_JSON,
                      body='{"username":"nobody","password":"x"}', test_lines=T401("unknown user")),
        ]),
        folder("Admin — entries", [
            make_item("List no token → 401", "GET", "{{baseAdmin}}/api/admin/entries",
                      test_lines=T401("no token")),
            make_item("List with token (captures entryId)", "GET",
                      "{{baseAdmin}}/api/admin/entries", headers=HDRS_AUTH_ONLY, test_lines=LIST_TESTS),
            make_item("Filter PENDING → 200", "GET",
                      "{{baseAdmin}}/api/admin/entries?status=PENDING", headers=HDRS_AUTH_ONLY,
                      test_lines=FILTER_TESTS),
            make_item("Filter BOGUS status → 400", "GET",
                      "{{baseAdmin}}/api/admin/entries?status=BOGUS", headers=HDRS_AUTH_ONLY,
                      test_lines=BOGUS_STATUS),
            make_item("Patch PENDING→APPROVED → 200", "PATCH",
                      "{{baseAdmin}}/api/admin/entries/{{entryId}}?status=APPROVED",
                      headers=HDRS_AUTH_ONLY,
                      test_lines=["pm.test('Status 200', () => pm.response.to.have.status(200));"]),
            make_item("Patch APPROVED→PENDING illegal → 409", "PATCH",
                      "{{baseAdmin}}/api/admin/entries/{{entryId}}?status=PENDING",
                      headers=HDRS_AUTH_ONLY,
                      test_lines=["pm.test('Status 409', () => pm.response.to.have.status(409));",
                                  "pm.test('transition error', () => pm.expect(pm.response.json().message).to.include('APPROVED'));"]),
            make_item("Patch APPROVED→INVITED → 200", "PATCH",
                      "{{baseAdmin}}/api/admin/entries/{{entryId}}?status=INVITED",
                      headers=HDRS_AUTH_ONLY,
                      test_lines=["pm.test('Status 200', () => pm.response.to.have.status(200));"]),
            make_item("Patch INVITED→REJECTED terminal → 409", "PATCH",
                      "{{baseAdmin}}/api/admin/entries/{{entryId}}?status=REJECTED",
                      headers=HDRS_AUTH_ONLY,
                      test_lines=["pm.test('Status 409', () => pm.response.to.have.status(409));",
                                  "pm.test('INVITED terminal', () => pm.expect(pm.response.json().message).to.include('INVITED'));"]),
            make_item("Patch nonexistent 999999 → 404", "PATCH",
                      "{{baseAdmin}}/api/admin/entries/999999?status=APPROVED",
                      headers=HDRS_AUTH_ONLY,
                      test_lines=["pm.test('Status 404', () => pm.response.to.have.status(404));",
                                  "pm.test('mentions 999999', () => pm.expect(pm.response.json().message).to.include('999999'));"]),
        ]),
        folder("Admin — bulk", [
            make_item("Get PENDING entries (stores bulkIds)", "GET",
                      "{{baseAdmin}}/api/admin/entries?status=PENDING",
                      headers=HDRS_AUTH_ONLY, test_lines=GET_PENDING_TESTS),
            make_item("Bulk all-success [bulkId1,bulkId2] → 200", "POST",
                      "{{baseAdmin}}/api/admin/entries/bulk", headers=HDRS_AUTH,
                      body='{"ids":[{{bulkId1}},{{bulkId2}}],"newStatus":"APPROVED"}',
                      test_lines=BULK_OK),
            make_item("Bulk partial [bulkId3,999999] → 207", "POST",
                      "{{baseAdmin}}/api/admin/entries/bulk", headers=HDRS_AUTH,
                      body='{"ids":[{{bulkId3}},999999],"newStatus":"APPROVED"}',
                      test_lines=BULK_207),
            make_item("Bulk empty ids → 400", "POST",
                      "{{baseAdmin}}/api/admin/entries/bulk", headers=HDRS_AUTH,
                      body='{"ids":[],"newStatus":"APPROVED"}', test_lines=B400),
            make_item("Bulk null status → 400", "POST",
                      "{{baseAdmin}}/api/admin/entries/bulk", headers=HDRS_AUTH,
                      body='{"ids":[1],"newStatus":null}', test_lines=B400),
        ]),
        folder("Referral + email", [
            make_item("Approve referee (EMAIL_B)", "PATCH",
                      "{{baseAdmin}}/api/admin/entries/{{refEntryId}}?status=APPROVED",
                      headers=HDRS_AUTH_ONLY,
                      test_lines=["pm.test('Status 200', () => pm.response.to.have.status(200));"]),
            make_item("Mailpit — >= 2 emails for runEmail", "GET",
                      "{{mailpit}}/api/v1/messages", test_lines=MAILPIT_TESTS),
        ]),
    ]
}

out = json.dumps(collection, indent=2)
with open("C:/Users/Tushar/IdeaProjects/Waitlist/postman/waitlist.postman_collection.json", "w", encoding="utf-8") as f:
    f.write(out)
json.loads(out)  # validate
total_reqs = sum(len(f["item"]) for f in collection["item"])
print(f"Written {len(out)} bytes, {total_reqs} requests, JSON valid")
