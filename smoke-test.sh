#!/bin/bash
# 冒烟测试：覆盖 P0/P1/P2 核心 API 流程
BASE=http://localhost:8080/api
PASS=0; FAIL=0
check() { if [ "$1" = "$2" ]; then PASS=$((PASS+1)); echo "PASS: $3"; else FAIL=$((FAIL+1)); echo "FAIL: $3 (期望 $2 实际 $1)"; fi }

# 1. 注册（弱密码应被拒绝）
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/register -H 'Content-Type: application/json' -d '{"email":"weak@test.com","password":"123","name":"x","teamName":"t"}')
check "$CODE" "400" "弱密码被拒绝"

# 1.5 注册管理员（幂等：已注册则忽略）
curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' -d '{"email":"admin@test.com","password":"Passw0rd!abc","name":"管理员","teamName":"测试播客团队"}' > /dev/null

# 2. 登录
TOKEN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@test.com","password":"Passw0rd!abc"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
check "$([ -n "$TOKEN" ] && echo ok)" "ok" "登录获取 access_token"
AUTH="Authorization: Bearer $TOKEN"

# 3. 密码错误
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@test.com","password":"Wrong123!xx"}')
check "$CODE" "401" "错误密码返回 401"

# 4. 创建节目（含结构模板）
POD=$(curl -s -X POST $BASE/podcasts -H "$AUTH" -H 'Content-Type: application/json' -d '{"name":"增长访谈","type":"INTERVIEW","updateFrequency":"每周","targetDuration":2400,"structureTemplate":[{"name":"开场","durationSec":30},{"name":"主题讨论","durationSec":1800}]}')
PID=$(echo "$POD" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
check "$([ "$PID" -gt 0 ] && echo ok)" "ok" "创建节目"

# 5. 创建单集
EP=$(curl -s -X POST $BASE/podcasts/$PID/episodes -H "$AUTH" -H 'Content-Type: application/json' -d '{"number":1,"title":"第一期：冷启动","theme":"聊聊冷启动","recordDate":"2026-07-20"}')
EID=$(echo "$EP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
check "$([ "$EID" -gt 0 ] && echo ok)" "ok" "创建单集"

# 6. 上传音频（生成测试 WAV）
python3 -c "
import struct, math, wave
w = wave.open('/tmp/test.wav','wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(8000)
frames = b''.join(struct.pack('<h', int(8000*math.sin(2*math.pi*440*i/8000))) for i in range(8000*5))
w.writeframes(frames); w.close()"
UP=$(curl -s -X POST $BASE/episodes/$EID/audio -H "$AUTH" -F "file=@/tmp/test.wav")
VID=$(echo "$UP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
check "$([ -n "$VID" ] && echo ok)" "ok" "上传音频 v1"
DUR=$(echo "$UP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["durationMs"] or 0)')
check "$([ "$DUR" -gt 4000 ] && echo ok)" "ok" "提取时长元数据 (durationMs=$DUR)"

# 7. 伪装文件头应被拒
echo "not an audio file at all" > /tmp/fake.mp3
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/episodes/$EID/audio -H "$AUTH" -F "file=@/tmp/fake.mp3")
check "$CODE" "400" "伪造文件头被拒绝"

# 8. 签名播放 URL + 流式播放
PLAY=$(curl -s $BASE/audio/$VID/play-url -H "$AUTH" | python3 -c 'import sys,json;print(json.load(sys.stdin)["url"])')
CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080$PLAY")
check "$CODE" "200" "签名 URL 播放"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Range: bytes=0-99" "http://localhost:8080$PLAY")
check "$CODE" "206" "Range 流式播放"
CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/audio/stream/$VID?exp=1&sig=bad")
check "$CODE" "401" "过期/错误签名被拒绝"

# 9. 波形数据
PEAKS=$(curl -s $BASE/audio/$VID/waveform -H "$AUTH" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(len(json.loads(d["peaks"])))')
check "$PEAKS" "1000" "预生成波形 1000 峰值点"

# 10. 点标记 + 段标记
M1=$(curl -s -X POST $BASE/episodes/$EID/markers -H "$AUTH" -H 'Content-Type: application/json' -d '{"startTimeMs":1500,"type":"MISSPEAK","description":"口误：说错公司名"}')
M1ID=$(echo "$M1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
check "$([ -n "$M1ID" ] && echo ok)" "ok" "创建点标记"
M2=$(curl -s -X POST $BASE/episodes/$EID/markers -H "$AUTH" -H 'Content-Type: application/json' -d '{"startTimeMs":2000,"endTimeMs":3500,"type":"BGM","description":"此处加背景音乐"}')
check "$(echo "$M2" | python3 -c 'import sys,json;print(json.load(sys.stdin)["endTimeMs"])')" "3500" "创建时间段标记"

# 11. 标记筛选
CNT=$(curl -s "$BASE/episodes/$EID/markers?type=MISSPEAK" -H "$AUTH" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')
check "$CNT" "1" "按类型筛选标记"
CNT=$(curl -s "$BASE/episodes/$EID/markers?keyword=%E8%83%8C%E6%99%AF" -H "$AUTH" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')
check "$CNT" "1" "关键词搜索标记"

# 12. 标记状态流转
curl -s -X PUT $BASE/markers/$M1ID/status -H "$AUTH" -H 'Content-Type: application/json' -d '{"status":"IN_PROGRESS"}' > /dev/null
ST=$(curl -s -X PUT $BASE/markers/$M1ID/status -H "$AUTH" -H 'Content-Type: application/json' -d '{"status":"RESOLVED"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])')
check "$ST" "RESOLVED" "标记状态流转"

# 13. 上传 v2，验证版本对比与标记迁移
python3 -c "
import struct, math, wave
w = wave.open('/tmp/test2.wav','wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(8000)
frames = b''.join(struct.pack('<h', int(8000*math.sin(2*math.pi*440*i/8000))) for i in range(8000*6))
w.writeframes(frames); w.close()"
curl -s -X POST $BASE/episodes/$EID/audio -H "$AUTH" -F "file=@/tmp/test2.wav" > /tmp/v2.json
VID2=$(python3 -c 'import json;print(json.load(open("/tmp/v2.json"))["id"])')
DIFF=$(curl -s $BASE/episodes/$EID/compare 2>/dev/null; curl -s "$BASE/episodes/$EID/versions/compare" -H "$AUTH" | python3 -c 'import sys,json;print(json.load(sys.stdin)["durationDiffMs"])')
check "$([ "$DIFF" -gt 0 ] && echo ok)" "ok" "版本时长对比 (diff=${DIFF}ms)"
MIG=$(curl -s "$BASE/episodes/$EID/markers?versionId=$(curl -s $BASE/episodes/$EID/versions -H "$AUTH" | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["id"])')" -H "$AUTH" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')
check "$MIG" "1" "未关闭标记迁移到新版本（已解决的不迁移）"

# 14. 转写导入 + 编辑
curl -s -X POST $BASE/audio/$VID/transcript/import -H "$AUTH" -H 'Content-Type: application/json' -d '{"segments":[{"startTimeMs":0,"endTimeMs":2500,"text":"大家好，欢迎来到节目","speaker":"主播A"},{"startTimeMs":2500,"endTimeMs":5000,"text":"今天聊聊冷启动","speaker":"嘉宾B"}]}' > /dev/null
SEG=$(curl -s $BASE/audio/$VID/transcript -H "$AUTH" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')
check "$SEG" "2" "导入转写片段（含说话人）"

# 15. 任务
TASK=$(curl -s -X POST $BASE/episodes/$EID/tasks -H "$AUTH" -H 'Content-Type: application/json' -d '{"description":"剪掉口误","assigneeId":1,"dueDate":"2026-08-10"}')
check "$(echo "$TASK" | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])')" "TODO" "创建任务"

# 16. 单集状态流转
ST=$(curl -s -X PUT $BASE/episodes/$EID/status -H "$AUTH" -H 'Content-Type: application/json' -d '{"status":"ROUGH_CUT"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])')
check "$ST" "ROUGH_CUT" "单集状态流转"

# 17. 分发：平台 + 分发任务 + 状态
PLAT=$(curl -s -X POST $BASE/platforms -H "$AUTH" -H 'Content-Type: application/json' -d '{"name":"小宇宙","accountName":"growth"}')
PLATID=$(echo "$PLAT" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
DIST=$(curl -s -X POST $BASE/episodes/$EID/distributions -H "$AUTH" -H 'Content-Type: application/json' -d "{\"platformId\":$PLATID,\"platformDataJson\":\"{\\\"shownotes\\\":\\\"txt\\\"}\",\"scheduledAt\":\"2026-08-15T10:00:00\"}")
DISTID=$(echo "$DIST" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
check "$([ -n "$DISTID" ] && echo ok)" "ok" "创建分发任务"
ST=$(curl -s -X PUT $BASE/distributions/$DISTID/status -H "$AUTH" -H 'Content-Type: application/json' -d '{"status":"SUBMITTED"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])')
check "$ST" "SUBMITTED" "分发状态流转（自动记录 submittedAt）"

# 18. 发布日历
CAL=$(curl -s "$BASE/calendar?from=2026-08-01T00:00:00&to=2026-08-31T23:59:59" -H "$AUTH" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')
check "$CAL" "1" "发布日历"

# 19. RSS feed
RSS=$(curl -s $BASE/rss/podcasts/$PID -H "$AUTH" | head -c 60)
check "$(echo "$RSS" | grep -c 'rss version')" "1" "RSS 2.0 feed"

# 20. 素材库 + 使用追踪
ASSET=$(curl -s -X POST $BASE/assets -H "$AUTH" -H 'Content-Type: application/json' -d '{"name":"开场曲","type":"AUDIO","category":"开场音乐"}')
ASSETID=$(echo "$ASSET" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
curl -s -X POST $BASE/assets/$ASSETID/usages -H "$AUTH" -H 'Content-Type: application/json' -d "{\"episodeId\":$EID,\"positionMs\":0}" > /dev/null
UC=$(curl -s $BASE/assets -H "$AUTH" | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["usageCount"])')
check "$UC" "1" "素材使用追踪"

# 21. 统计
EPSTATS=$(curl -s $BASE/stats/episodes/$EID -H "$AUTH" | python3 -c 'import sys,json;print(json.load(sys.stdin)["markerCount"])')
check "$EPSTATS" "3" "单集统计（标记数含迁移）"
TEAM=$(curl -s $BASE/stats/team -H "$AUTH" | python3 -c 'import sys,json;print(json.load(sys.stdin)["episodeCount"])')
check "$TEAM" "1" "团队效率统计"

# 22. 访客分享
SHARE=$(curl -s -X POST $BASE/episodes/$EID/share -H "$AUTH" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
VIEW=$(curl -s $BASE/share/$SHARE | python3 -c 'import sys,json;print("ok" if "audioUrl" in json.load(sys.stdin) else "no")')
check "$VIEW" "ok" "访客分享链接访问（免登录）"

# 23. 团队隔离：另一团队的 token 访问本团队单集应 403
T2=$(curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' -d '{"email":"other@test.com","password":"Passw0rd!abc","name":"外人","teamName":"别的团队"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
CODE=$(curl -s -o /dev/null -w "%{http_code}" $BASE/episodes/$EID -H "Authorization: Bearer $T2")
check "$CODE" "403" "跨团队数据隔离"

# 23b. 团队隔离：本团队单集 ID + 他团队 versionId 读标记应 403
POD2=$(curl -s -X POST $BASE/podcasts -H "Authorization: Bearer $T2" -H 'Content-Type: application/json' -d '{"name":"别人的节目","type":"NEWS"}')
PID2=$(echo "$POD2" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
EP2=$(curl -s -X POST $BASE/podcasts/$PID2/episodes -H "Authorization: Bearer $T2" -H 'Content-Type: application/json' -d '{"number":1,"title":"别人的单集"}')
EID2=$(echo "$EP2" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/episodes/$EID2/markers?versionId=$VID" -H "Authorization: Bearer $T2")
check "$CODE" "403" "跨团队 versionId 读标记被拒绝"

# 23c. 密码找回：统一话术、响应不含 token（防邮箱枚举）
MSG1=$(curl -s -X POST $BASE/auth/forgot-password -H 'Content-Type: application/json' -d '{"email":"admin@test.com"}')
MSG2=$(curl -s -X POST $BASE/auth/forgot-password -H 'Content-Type: application/json' -d '{"email":"no-such-user@test.com"}')
check "$(echo "$MSG1" | python3 -c 'import sys,json;print("resetToken" in json.load(sys.stdin))')" "False" "找回密码响应不含 token"
check "$([ "$MSG1" = "$MSG2" ] && echo same)" "same" "找回密码统一话术（不暴露邮箱是否存在）"

# 23d. 密码重置全链路：从 DB 取 token（开发环境），重置后新密码可登录
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^podcast-mysql$'; then
  RT=$(docker exec podcast-mysql mysql -uroot -ppodcastroot -N -e "SELECT token FROM podcast_db.password_reset_tokens WHERE used=0 ORDER BY id DESC LIMIT 1" 2>/dev/null)
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/reset-password -H 'Content-Type: application/json' -d "{\"token\":\"$RT\",\"newPassword\":\"NewPass123!x\"}")
  check "$CODE" "200" "密码重置成功（token 经邮件通道下发）"
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@test.com","password":"NewPass123!x"}')
  check "$CODE" "200" "新密码登录成功"
  # 恢复管理员密码（再发起一次找回）
  curl -s -X POST $BASE/auth/forgot-password -H 'Content-Type: application/json' -d '{"email":"admin@test.com"}' > /dev/null
  RT2=$(docker exec podcast-mysql mysql -uroot -ppodcastroot -N -e "SELECT token FROM podcast_db.password_reset_tokens WHERE used=0 ORDER BY id DESC LIMIT 1" 2>/dev/null)
  curl -s -X POST $BASE/auth/reset-password -H 'Content-Type: application/json' -d "{\"token\":\"$RT2\",\"newPassword\":\"Passw0rd!abc\"}" > /dev/null
  TOKEN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@test.com","password":"Passw0rd!abc"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
  AUTH="Authorization: Bearer $TOKEN"
else
  echo "SKIP: 密码重置全链路（无 podcast-mysql 容器）"
fi

# 23e. 上传后回写 final_audio_url
FA=$(curl -s $BASE/episodes/$EID -H "$AUTH" | python3 -c 'import sys,json;print(json.load(sys.stdin)["finalAudioUrl"] or "")')
check "$FA" "/api/audio/stream/$VID2" "上传后回写 final_audio_url"

# 23f. RSS enclosure（含 length）+ 真实地址 + 按文件类型输出 MIME
curl -s -X PUT $BASE/episodes/$EID/status -H "$AUTH" -H 'Content-Type: application/json' -d '{"status":"DISTRIBUTING"}' > /dev/null
RSSXML=$(curl -s $BASE/rss/podcasts/$PID -H "$AUTH")
check "$(echo "$RSSXML" | grep -c '<enclosure')" "1" "RSS 含 enclosure"
check "$(echo "$RSSXML" | grep -c 'length=')" "1" "RSS enclosure 含 length"
check "$(echo "$RSSXML" | grep -c 'example.com')" "0" "RSS 无硬编码 example.com"
check "$(echo "$RSSXML" | grep -c 'localhost:8080/api/audio/stream')" "1" "RSS enclosure 为真实签名地址"
check "$(echo "$RSSXML" | grep -c 'type="audio/wav"')" "1" "RSS enclosure type 按真实文件类型"

# 23g. RSS 公开可抓：平台视角不带任何凭证
RSSPUB=$(curl -s $BASE/rss/podcasts/$PID)
check "$(echo "$RSSPUB" | grep -c 'rss version')" "1" "不带凭证抓取 RSS feed（平台视角）"
check "$(echo "$RSSPUB" | grep -c '<enclosure')" "1" "公开 feed 同样含 enclosure"

# 24. 权限：剪辑师不能创建节目（邀请 token 从 mock 邮件 outbox 取，不依赖响应字段）
MAIL_LOG=${MOCK_MAIL_LOG:-backend/data/mock-mail.log}
INVRESP=$(curl -s -X POST $BASE/teams/invites -H "$AUTH" -H 'Content-Type: application/json' -d '{"email":"editor@test.com","role":"EDITOR"}')
check "$(echo "$INVRESP" | python3 -c 'import sys,json;print("inviteToken" in json.load(sys.stdin))')" "False" "邀请响应不含 token"
INVITE=$(grep -o 'invite?token=[A-Za-z0-9_-]*' "$MAIL_LOG" | tail -1 | cut -d= -f2)
check "$([ -n "$INVITE" ] && echo ok)" "ok" "邀请链接经 mock 邮件通道下发"
ET=$(curl -s -X POST $BASE/auth/accept-invite -H 'Content-Type: application/json' -d "{\"token\":\"$INVITE\",\"name\":\"剪辑师\",\"password\":\"Passw0rd!abc\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
check "$([ -n "$ET" ] && echo ok)" "ok" "邀请成员加入（剪辑师）"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/podcasts -H "Authorization: Bearer $ET" -H 'Content-Type: application/json' -d '{"name":"x","type":"NEWS"}')
check "$CODE" "403" "剪辑师无创建节目权限"

# 25. 审计日志
AUDIT=$(curl -s $BASE/teams/audit-logs -H "$AUTH" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')
check "$([ "$AUDIT" -gt 5 ] && echo ok)" "ok" "操作审计日志 ($AUDIT 条)"

# 26. 限流（登录接口 5 次/分钟）
for i in $(seq 1 6); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@test.com","password":"Wrong123!xx"}')
done
check "$CODE" "429" "登录接口限流 5次/分钟"

# 27. 伪造 X-Forwarded-For 不能绕过限流（未配置可信代理时按真实连接 IP 计数）
CODE=""
for i in $(seq 1 6); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/login \
    -H 'Content-Type: application/json' -H "X-Forwarded-For: 10.99.99.$i" \
    -d '{"email":"admin@test.com","password":"Wrong123!xx"}')
done
check "$CODE" "429" "伪造 XFF 连续登录仍触发 429"

echo "=============================="
echo "PASS: $PASS  FAIL: $FAIL"
