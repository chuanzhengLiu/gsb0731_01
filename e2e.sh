#!/bin/bash
set -e
BASE=http://localhost:8080/api
SECRET="e2e-test-secret-key-that-is-at-least-32-bytes-long"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AUDIO_FILE="$SCRIPT_DIR/backend/data/e2e/audio/e2efile001.mp3"

echo "== login =="
LOGIN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"e2e@test.com","password":"Test1234!@#$"}')
TOKEN=$(echo "$LOGIN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
AUTH="Authorization: Bearer $TOKEN"
echo "token=${TOKEN:0:30}..."

echo "== create podcast =="
POD=$(curl -s -X POST $BASE/podcasts -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"E2E Pod","type":"INTERVIEW"}')
PID=$(echo "$POD" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
echo "podcast id=$PID"

echo "== create episode =="
EP=$(curl -s -X POST $BASE/podcasts/$PID/episodes -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"title":"E2E Episode"}')
EID=$(echo "$EP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
echo "episode id=$EID"

echo "== insert audio versions via docker mysql =="
docker exec podcast-mysql-test mysql -uroot -ppodcastroot podcast_db -e "
DELETE FROM audio_versions WHERE episode_id=$EID;
INSERT INTO audio_versions (episode_id, version_number, file_url, file_name, mime_type, file_size, duration_ms, archived, uploaded_by, created_at, updated_at)
VALUES ($EID, 1, '/api/files/audio/e2efile001.mp3', 'e2efile001.mp3', 'audio/mpeg', 24468, 3000, 0, 1, NOW(6), NOW(6));
INSERT INTO audio_versions (episode_id, version_number, file_url, file_name, mime_type, file_size, duration_ms, archived, uploaded_by, created_at, updated_at)
VALUES ($EID, 2, '/api/files/audio/archived001.mp3', 'archived001.mp3', 'audio/mpeg', 24468, 3000, 1, 1, NOW(6), NOW(6));
UPDATE episodes SET status='FINALIZED', final_audio_url='/api/files/audio/e2efile001.mp3' WHERE id=$EID;
" 2>/dev/null
echo "inserted"

echo "== TEST 10: upload audio asset and verify it is playable =="
ASSET=$(curl -s -X POST $BASE/assets/audio -H "$AUTH" -F "file=@$AUDIO_FILE" -F "name=E2E Asset Audio")
echo "asset resp: $(echo "$ASSET" | head -c 300)"
ASSET_URL=$(echo "$ASSET" | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a.get("fileUrl") or "")')
echo "asset fileUrl=$ASSET_URL"
test -n "$ASSET_URL" || { echo "FAIL: asset fileUrl empty"; exit 1; }

python3 - "$SECRET" "$BASE" "$PID" "$EID" "$ASSET_URL" <<'PY'
import sys, hmac, hashlib, time, urllib.parse, urllib.request, json, re
secret, base, pid, eid, asset_url = sys.argv[1:6]

def sign(signed_path, url_path, ttl=3600, dl=False):
    # signed_path is the full canonical path including /api (what verify sees as requestURI)
    # url_path is the path appended to base (which already includes /api), so no /api prefix
    exp = int(time.time()) + ttl
    data = f"{signed_path}|{exp}|{1 if dl else 0}".encode()
    sig = hmac.new(secret.encode(), data, hashlib.sha256).hexdigest()
    sep = '&' if '?' in url_path else '?'
    return f"{base}{url_path}{sep}exp={exp}&sig={urllib.parse.quote(sig)}" + ("&dl=1" if dl else "")

def req(url, method='GET', headers=None):
    try:
        r = urllib.request.Request(url, method=method, headers=headers or {})
        with urllib.request.urlopen(r) as resp:
            return resp.status, resp.headers.get('Content-Type',''), resp.headers.get('Content-Range',''), resp.headers.get('Content-Disposition',''), resp.read()[:20]
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get('Content-Type',''), e.headers.get('Content-Range',''), e.headers.get('Content-Disposition',''), e.read()[:60]

# canonical signed path (what the server sees as requestURI, includes /api context path)
signed_audio = "/api/files/audio/e2efile001.mp3"
signed_archived = "/api/files/audio/archived001.mp3"
# url path appended to base (base already ends with /api)
url_audio = "/files/audio/e2efile001.mp3"
url_archived = "/files/audio/archived001.mp3"

print("\n=== TEST 1: signed URL plays (expect 200, audio/mpeg) ===")
u = sign(signed_audio, url_audio)
st, ct, cr, cd, body = req(u)
print(f"status={st} content-type={ct} bytes={len(body)}")
assert st == 200 and 'audio' in ct, f"FAIL: signed URL should play, got {st} {ct}"

print("\n=== TEST 2: no signature (expect 403) ===")
st, _, _, _, body = req(base + url_audio)
print(f"status={st} body={body[:80]}")
assert st == 403, f"FAIL: unsigned should be 403, got {st}"

print("\n=== TEST 3: tampered signature (expect 403) ===")
bad = base + url_audio + "?exp=9999999999&sig=deadbeef"
st, _, _, _, _ = req(bad)
print(f"status={st}")
assert st == 403, f"FAIL: tampered should be 403, got {st}"

print("\n=== TEST 4: expired URL (expect 401) ===")
old_exp = int(time.time()) - 60
data = f"{signed_audio}|{old_exp}|0".encode()
sig = hmac.new(secret.encode(), data, hashlib.sha256).hexdigest()
u = base + f"{url_audio}?exp={old_exp}&sig={sig}"
st, _, _, _, _ = req(u)
print(f"status={st}")
assert st == 401, f"FAIL: expired should be 401, got {st}"

print("\n=== TEST 5: Range request (expect 206 + Content-Range) ===")
u = sign(signed_audio, url_audio)
st, _, cr, _, _ = req(u, headers={'Range': 'bytes=0-99'})
print(f"status={st} content-range={cr}")
assert st == 206 and cr.startswith('bytes 0-99/'), f"FAIL: range should be 206, got {st} {cr}"

print("\n=== TEST 6: archived version streamed WITHOUT dl=1 (expect 403, online play blocked) ===")
u = sign(signed_archived, url_archived)
st, _, _, _, _ = req(u)
print(f"status={st}")
assert st == 403, f"FAIL: archived stream should be 403, got {st}"

print("\n=== TEST 7: archived version WITH dl=1 (expect 200 + attachment) ===")
u = sign(signed_archived, url_archived, dl=True)
st, _, _, cd, _ = req(u)
print(f"status={st} content-disposition={cd}")
assert st == 200 and 'attachment' in cd, f"FAIL: archived download should be 200 attachment, got {st} {cd}"

print("\n=== TEST 8: RSS feed only finalized episode, enclosure downloadable ===")
rss_url = base + f"/rss/podcasts/{pid}"
with urllib.request.urlopen(rss_url) as resp:
    rss = resp.read().decode()
assert "<item>" in rss, "FAIL: rss should contain the finalized item"
assert "e2efile001" in rss, "FAIL: rss enclosure should reference final audio"
assert "archived001" not in rss, "FAIL: rss must not include archived version"
m = re.search(r'<enclosure url="([^"]+)"', rss)
assert m, "FAIL: no enclosure tag found in RSS"
enc = m.group(1).replace('&amp;', '&')
print("enclosure:", enc[:120], "...")
st, ct, _, _, body = req(enc)
print(f"enclosure download status={st} content-type={ct}")
assert st == 200 and 'audio' in ct, f"FAIL: rss enclosure should download, got {st} {ct}"

print("\n=== TEST 10 (python): asset library audio playable via signed URL ===")
# asset_url already starts with /api (the context path); base also ends with /api, so strip the prefix
asset_req_url = asset_url
if asset_req_url.startswith("/api/"):
    asset_req_url = asset_req_url[4:]
print(f"asset url path: {asset_req_url}")
st, ct, _, _, body = req(base + asset_req_url)
print(f"asset audio status={st} content-type={ct}")
assert st == 200 and 'audio' in ct, f"FAIL: asset audio should play, got {st} {ct}"

print("\nALL PYTHON E2E ASSERTIONS PASSED")
PY

echo ""
echo "=== TEST 9: share an episode with NO audio (expect 200, no 500) ==="
NOAUDIO=$(curl -s -X POST $BASE/podcasts/$PID/episodes -H "$AUTH" -H 'Content-Type: application/json' -d '{"title":"NoAudio Ep"}')
NEID=$(echo "$NOAUDIO" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
echo "no-audio episode id=$NEID"
SHARE=$(curl -s -X POST $BASE/episodes/$NEID/share -H "$AUTH")
echo "share resp: $(echo "$SHARE" | head -c 200)"
STOKEN=$(echo "$SHARE" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
echo "share token=$STOKEN"
CODE=$(curl -s -o /tmp/share_resp.json -w "%{http_code}" "$BASE/share/$STOKEN")
echo "anonymous share GET status=$CODE"
cat /tmp/share_resp.json | python3 -m json.tool 2>/dev/null | head -25
test "$CODE" = "200" || { echo "FAIL: share no-audio returned $CODE"; exit 1; }
python3 -c 'import json; d=json.load(open("/tmp/share_resp.json")); assert d.get("latestVersion") is None, "latestVersion should be null"; print("latestVersion is null as expected")'

echo ""
echo "========================================"
echo "ALL E2E ASSERTIONS PASSED"
echo "========================================"
