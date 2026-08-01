# 独立播客制作与分发协作系统 — 工程需求文档

## 一、产品定位

面向5-20人的小型播客团队（如独立播客、知识付费栏目、企业内训音频），解决多人协作制作音频时沟通混乱、剪辑反馈分散、多平台分发状态难以追踪的问题。核心不是音频剪辑本身（假设用Audition/Logic等DAW），而是"音频时间轴协作标注"和"分发状态追踪"。

## 二、核心用户与场景

- **制作人/主编**：上传粗剪音频，在时间轴上标注修改意见，分配任务给剪辑师
- **剪辑师**：查看标注，修改后上传新版本，标记已解决
- **运营**：管理各平台分发账号，更新分发状态
- **主播/嘉宾**：听审成片，在有问题的时间点打标记

## 三、用户与权限体系

### 3.1 用户角色
- **团队管理员**：创建团队、管理成员、设置节目、查看所有数据
- **制作人/主编**：上传音频、添加标注、分配任务、审听定稿
- **剪辑师**：查看分配给自己的任务、上传修改版本、标记完成
- **运营**：管理分发平台、更新分发状态、管理RSS
- **主播/嘉宾**：听审音频、添加标记（只读访问被授权的集数）
- **访客（外部合作）**：通过分享链接临时访问特定集数，7天后失效

### 3.2 认证与授权
- 登录方式：邮箱+密码 / 第三方OAuth（Google/GitHub 可选）
- JWT Token 认证：access_token（2小时）+ refresh_token（7天）
- 团队隔离：用户加入团队后，只能访问本团队数据，跨团队数据完全隔离
- 密码策略：最小10位，字母+数字+特殊字符；支持密码找回（邮箱链接，30分钟有效）
- 会话管理：支持查看活跃会话、强制下线
- 邀请机制：管理员通过邮箱邀请成员，邀请链接24小时有效
- 操作审计：所有音频上传、标注增删、版本切换、分发状态变更记录到 audit_log

## 四、功能模块

### 4.1 节目项目管理
- 节目档案：名称、类型（访谈/叙事/知识/新闻）、更新频率、目标时长、固定板块结构
- 单集管理：集数、标题、主题、录制日期、状态（策划→录制→粗剪→精剪→审听→定稿→分发→已发布）
- 节目结构模板：可配置固定板块（如"开场（30秒）→嘉宾介绍（2分钟）→主题讨论（30分钟）→听众问答（10分钟）→结尾（1分钟）"），每集基于模板创建，实际时长自动对比模板
- 任务看板：每集的任务列表（谁负责什么、截止日期、状态）

### 4.2 音频时间轴协作（核心功能）
- 音频播放器：支持WAV/MP3/M4A，波形图展示（用Web Audio API或wavesurfer.js）
- 时间轴标注：
  - 在波形图上点击或拖拽选择时间段，添加标记
  - 标记类型：口误（需删除）、补录（需替换）、音量问题、背景音乐、音效插入、过渡不自然、事实待核实
  - 每条标记包含：时间范围、类型、描述文字、截图（可选）、提出人、提出时间
  - 标记状态：待处理/处理中/已解决/已忽略
- 版本对比：上传新版本后，自动对比两个版本的时长差异，保留历史标记（时间偏移自动适配）
- 筛选与搜索：按标记类型、提出人、状态筛选；按关键词搜索标记描述
- 快捷键：空格播放/暂停、M添加标记、左右箭头微调时间

### 4.3 转写文本对齐
- 上传音频后，调用Whisper API（或本地Whisper模型）生成转写文本
- 转写结果与音频时间戳对齐：点击文本中的句子，播放器跳转到对应时间点
- 在转写文本上直接添加标记（适合快速定位，不用反复听）
- 说话人分离：区分不同说话人（主播A、嘉宾B），用不同颜色显示
- 转写编辑：人工修正转写错误，修正后时间戳自动调整

### 4.4 分发管理
- 平台账号管理：小宇宙、Apple Podcasts、Spotify、网易云音乐、喜马拉雅等
- 每集分发任务：选择要分发的平台，填写各平台专属信息（如小宇宙的shownotes格式、Apple的分类标签）
- 分发状态追踪：未开始→已提交→审核中→已上线→被拒绝，每个平台独立状态
- RSS管理：生成符合Podcast RSS 2.0标准的feed，包含 enclosure、duration、shownotes
- 发布日历：日历视图显示未来已排期的发布计划

### 4.5 素材库
- 音频素材：开场音乐、过渡音效、广告片花，分类管理，支持试听
- 文本素材：固定口播文案、赞助商口播、节目slogan
- 素材使用追踪：统计每个素材在每集中使用的位置和次数

### 4.6 数据统计
- 单集数据：时长、标记数量（反映修改轮次）、从录制到发布的周期天数
- 团队效率：人均处理标记数、平均审听轮次、逾期任务数
- 分发覆盖：各平台上架率、平均审核时长

## 五、技术架构

- **后端**：Java + Spring Boot 3
- **前端**：React + shadcn/ui
- **数据库**：MySQL 8.0
- **ORM**：Spring Data JPA + Flyway 迁移
- **波形图**：wavesurfer.js
- **音频处理**：后端 FFmpeg 生成波形图缩略图、提取音频元数据
- **转写**：可选接入 OpenAI Whisper API，或本地部署 Whisper（small模型）
- **文件存储**：本地文件系统（开发）/ MinIO 单节点（生产）
- **部署**：Docker + docker-compose

## 六、Docker Compose 部署

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: podcastroot
      MYSQL_DATABASE: podcast_db
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"
  backend:
    build: ./backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/podcast_db?useSSL=false&serverTimezone=UTC
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: podcastroot
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8080:8080"
    depends_on:
      - mysql
  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      - backend
volumes:
  mysql_data:
```

启动命令：`docker-compose up -d`

Spring Boot 额外配置：
- 使用 `spring-boot-starter-security` + `jjwt` 处理 JWT
- 使用 `spring-boot-starter-validation` 做输入校验
- 使用 `bucket4j` 做接口限流

## 七、数据模型要点

- User：id、email、password_hash、name、role、team_id、created_at
- Team：id、name、created_by、created_at
- TeamMember：id、team_id、user_id、role_in_team、joined_at
- Podcast：id、team_id、name、type、update_frequency、target_duration、structure_template_json
- Episode：id、podcast_id、number、title、theme、record_date、status、final_audio_url
- Task：id、episode_id、assignee_id、description、due_date、status
- AudioVersion：id、episode_id、version_number、file_url、duration、uploaded_by、created_at
- TimelineMarker：id、audio_version_id、start_time_ms、end_time_ms、type、description、status、created_by
- TranscriptSegment：id、audio_version_id、start_time_ms、end_time_ms、text、speaker
- Distribution：id、episode_id、platform_id、status、submitted_at、published_at、platform_data_json
- Platform：id、name、rss_required_fields_json、category_options_json
- Asset：id、team_id、name、type、file_url、usage_count
- AuditLog：id、user_id、action、target_type、target_id、details、ip_address、created_at

## 八、安全要求

- 密码 bcrypt 加密（Spring Security 默认 BCryptPasswordEncoder，strength=12）
- JWT secret key 从环境变量读取
- SQL 注入防护：JPA 参数化查询
- XSS 防护：前端 React 自动转义 + 后端输入校验
- CSRF 防护：SameSite=Strict Cookie + CSRF Token（Spring Security 默认）
- 文件上传安全：限制音频类型（wav/mp3/m4a），大小不超过 500MB，后端校验文件头
- 音频访问控制：音频 URL 带签名 token，过期失效
- API 速率限制：bucket4j 配置，通用 100次/分钟，登录 5次/分钟，上传 10次/分钟
- HTTPS 强制 + HSTS
- 团队隔离：所有查询必须带 team_id 过滤，防止跨团队数据泄露
- 访客链接：分享链接带随机 token，7天过期，访问记录日志

## 九、非功能需求

- 波形图加载性能：1小时音频的波形图应在5秒内生成并展示，后端预生成波形数据JSON
- 音频播放流畅：支持大文件流式播放，进度条拖拽响应及时
- 版本管理：每集保留最近10个音频版本，旧版本自动归档（可下载但不在线播放）
- 不接入在线支付、不做听众评论系统、不做播放量统计（各平台数据不开放）
- 权限：制作人可改一切，剪辑师只能操作分配给自己的，运营只能操作分发

## 十、开发优先级

P0：团队/用户认证、节目/单集管理、音频上传与波形图播放、基础时间轴标记（点标记）
P1：时间段标记、标记状态流转、版本管理、转写文本对齐
P2：分发管理、素材库、节目结构模板、数据统计、访客分享
