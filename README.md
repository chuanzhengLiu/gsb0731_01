# 独立播客制作与分发协作系统

面向 5–20 人小型播客团队的制作协作系统，聚焦**音频时间轴协作标注**与**多平台分发状态追踪**。
本仓库按需求文档（完整保存于 [`docs/requirements.md`](docs/requirements.md)）实现，技术栈与约束严格遵循文档：

- **后端**：Java 17 + Spring Boot 3（Spring Security + Spring Data JPA + Flyway + jjwt + bucket4j）
- **前端**：React + shadcn/ui（Vite + TypeScript + Tailwind + wavesurfer.js）
- **数据库**：MySQL 8.0
- **音频处理**：后端 FFmpeg 提取元数据 + 预生成波形数据 JSON
- **部署**：Docker + docker-compose

> 需求文档中明确**不做**的功能已严格排除：**在线支付、听众评论、播放量统计**。

## 开发进度（按文档优先级 P0 → P1 → P2）

当前已完成 **P0（完整）**：

| P0 能力 | 状态 |
| --- | --- |
| 团队 / 用户认证（注册即建团队，JWT access 2h + refresh 7d，BCrypt strength=12） | ✅ |
| 团队数据隔离（所有查询按 `team_id` 过滤） | ✅ |
| 会话管理（查看活跃会话、强制下线）、操作审计 `audit_log` | ✅ |
| 接口限流 bucket4j（通用 100/min、登录 5/min、上传 10/min） | ✅ |
| 节目 / 单集管理（含单集状态机 策划→…→已发布） | ✅ |
| 音频上传（wav/mp3/m4a，≤500MB，文件头校验）、FFmpeg 波形预生成 | ✅ |
| 签名 token 的音频流式播放（支持 Range）、旧版本归档（保留最近 10 个） | ✅ |
| 波形图播放（wavesurfer.js）+ 基础时间轴点标记（增删改查、筛选、搜索、快捷键） | ✅ |

P1 / P2 将在 P0 基础上继续开发（时间段标记、版本对比、转写对齐、分发管理、素材库、
节目结构模板、数据统计、访客分享）。数据库 schema（[`V1__init_schema.sql`](backend/src/main/resources/db/migration/V1__init_schema.sql)）
已前瞻性地为 P1/P2 表建好结构，避免后续破坏性迁移。

## 目录结构

```
backend/    Spring Boot 3 后端（Maven）
frontend/   React + shadcn/ui 前端（Vite）
docker-compose.yml
```

## 快速启动（Docker，推荐）

```bash
# 可选：设置生产密钥
export JWT_SECRET="至少32字节的强随机密钥________________"
export MEDIA_URL_SECRET="音频URL签名密钥"

docker-compose up -d
```

- 前端： http://localhost （nginx，API 反向代理到 backend）
- 后端： http://localhost:8080
- MySQL： localhost:3306（库 `podcast_db`）

首次进入前端点击「注册新团队」，注册者即成为该团队管理员（ADMIN）。

## 本地开发

### 后端

```bash
cd backend
# 本机全局 ~/.m2/settings.xml 指向了内网 Nexus，离线不可达；
# 故提供项目级直连 Maven Central 的 settings：
mvn -s .mvn/settings.xml spring-boot:run
```

> 本地运行需要一个可用的 MySQL 8.0（或先 `docker-compose up -d mysql`）。
> 运行 FFmpeg 相关功能需本机安装 `ffmpeg`/`ffprobe`；未安装时上传仍成功，
> 仅波形/时长为空（前端回退为客户端解码波形）。

运行测试（使用 H2 内存库，无需 MySQL）：

```bash
mvn -s .mvn/settings.xml test
```

### 前端

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173 ，/api 代理到 8080
```

## 主要 API（P0）

| 方法 & 路径 | 说明 |
| --- | --- |
| `POST /api/auth/register` | 注册并创建团队（返回令牌） |
| `POST /api/auth/login` | 登录 |
| `POST /api/auth/refresh` | 刷新令牌（轮换 refresh token） |
| `POST /api/auth/logout` | 注销当前会话 |
| `GET  /api/auth/sessions` | 查看活跃会话 |
| `POST /api/auth/sessions/revoke-all` | 强制下线全部会话 |
| `GET/POST/PUT/DELETE /api/podcasts` | 节目管理（ADMIN/PRODUCER 可写） |
| `GET/POST /api/podcasts/{id}/episodes` | 单集管理 |
| `PUT/DELETE /api/episodes/{id}` | 单集更新 / 删除 |
| `POST /api/episodes/{id}/audio-versions` | 上传音频版本（multipart，限流 upload） |
| `GET  /api/audio-versions/{id}/waveform` | 预生成波形 JSON |
| `GET  /api/media/stream/{id}?token=...` | 签名 token 流式播放（支持 Range） |
| `GET/POST /api/audio-versions/{id}/markers` | 标记列表 / 筛选 / 搜索 / 新增 |
| `PUT/DELETE /api/markers/{id}` | 标记状态流转 / 删除 |

## 安全实现要点（对应文档 §8）

- 密码 BCrypt（strength=12），密码策略：≥10 位且含字母+数字+特殊字符
- JWT secret 从环境变量 `JWT_SECRET` 读取
- JPA 参数化查询防 SQL 注入；React 自动转义 + 后端校验防 XSS
- 音频 URL 带 HMAC 签名 token，短期过期失效
- bucket4j 限流：通用 100/min、登录 5/min、上传 10/min
- HSTS 头；团队隔离贯穿所有资源访问（`AccessGuard`）
- 文件上传：扩展名白名单 + 文件头（magic bytes）校验 + 500MB 上限

## 需求文档

原始工程需求文档完整保存于 [`docs/requirements.md`](docs/requirements.md)。核心约束：技术栈固定为
Spring Boot 3 + React + MySQL 8.0；不实现在线支付、听众评论、播放量统计。
