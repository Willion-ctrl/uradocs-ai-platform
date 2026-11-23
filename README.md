# AuraDocs

AuraDocs 是一款基于 **Spring Boot 3 + Vue 3** 构建的企业级智能知识库与 AI 协作平台，提供从多租户权限隔离、三栏式 Markdown 文档协作，到基于文档上下文的 SSE 流式 AI 问答、一键 AI 摘要持久化与多字段全文检索的完整全栈方案。

---

## 核心功能

* **认证与多租户空间隔离**
  * 基于 JWT 的无状态认证体系，涵盖注册、登录及身份校验（`/api/auth/me`）。
  * 前端集成 Pinia 状态持久化、Axios 请求拦截器自动注入 Token 与 Vue Router 全局路由守卫。
  * 通过 `UserContext` 与空间可见性（私有 / 团队公开）实现读写权限隔离，文档自动继承所属空间权限。
* **三栏式知识库工作台**
  * 支持知识空间创建与切换、文档增删改查以及 MyBatis-Plus 逻辑删除（`@TableLogic`）。
  * 内置 Markdown 源码编辑与实时预览切换，支持 `Ctrl + S` 快捷键保存。
* **文档上下文 SSE 流式问答**
  * 后端基于 `SseEmitter` 推送 `meta`、`message`、`done` 事件；前端通过原生 `fetch` 与 `ReadableStream` 解析实现逐字打字机效果，支持携带自定义 `Authorization` 请求头及 `sessionId` 多轮对话记忆。
* **一键 AI 摘要与持久化**
  * 同步调用大模型提炼 100 字以内核心摘要并落库（`kb_doc.ai_summary`）。
  * 前端触发摘要前自动静默保存未落库改动，顶部展示摘要横幅并在文档列表标记 `AI` 状态。
* **多字段防抖检索**
  * 支持按文档标题、Markdown 正文与 AI 摘要三字段联合模糊检索，前端内置 300ms 防抖机制。

---

## 工程与架构亮点

1. **非事务外调大模型防连接池耗尽**：AI 摘要接口脱离 `@Transactional`，采用“查库鉴权 -> 事务外 HTTP 调用大模型 -> 落库更新”三段式，避免外部 API 耗时长期占用 HikariCP 数据库连接。
2. **局部更新防并发写覆盖**：摘要落库使用 MyBatis-Plus `LambdaUpdateWrapper` 仅更新 `ai_summary` 单字段，防止大模型生成期间覆盖用户正在编辑保存的 `content_md`。
3. **列表与检索大字段裁剪**：文档列表与搜索接口通过 `select` 过滤剔除 `content_md` 大文本字段（不影响 `WHERE` 条件检索），仅在点开单篇文档时按需拉取完整正文，降低数据库与网络 I/O。
4. **跨空间检索防越权**：全局搜索未传 `spaceId` 时，服务端先查询当前用户可读空间集合（自有空间与团队公开空间），杜绝私有文档泄露。
5. **异常收敛与输入保护**：全局异常统一收敛至 `GlobalExceptionHandler` 与 `ErrorCode` 枚举；大模型摘要输入内置 8000 字符截断保护防止触发 Token 上限。

---

## 技术栈

| 层级 | 核心技术 |
| :--- | :--- |
| 后端 | Java 17+、Spring Boot 3.4.5、MyBatis-Plus、MySQL 8.0、JWT、SseEmitter、JDK HttpClient |
| 前端 | Vue 3、Vite、Element Plus、Pinia、Vue Router、Axios、Marked.js |

---

## 项目结构

```text
uradocs-ai-platform/
├── auradoc-backend/          # Spring Boot 3 后端服务
│   └── src/main/java/.../
│       ├── common/           # 统一响应体 Result、ErrorCode、GlobalExceptionHandler
│       ├── controller/       # Auth、KbSpace、KbDoc、AiChat 接口层
│       ├── entity/           # SysUser、KbSpace、KbDoc 等实体类
│       ├── mapper/           # MyBatis-Plus Mapper 接口
│       ├── security/         # JWT 工具、拦截器与 UserContext
│       └── service/          # 业务逻辑层与大模型调用封装
├── auradocs-frontend/        # Vue 3 + Vite 前端工程
│   └── src/
│       ├── api/              # auth、kb、ai (SSE 流式) 接口封装
│       ├── router/           # 路由配置与全局前置守卫
│       ├── stores/           # Pinia 用户状态管理
│       ├── utils/            # Axios 请求与响应拦截器
│       └── views/            # Login 登录注册页、Workspace 三栏工作台
└── README.md
```

---

## 快速开始

### 1. 环境要求

* JDK 17+ / Maven 3.8+
* MySQL 8.0+
* Node.js 18+ / npm

### 2. 配置后端本地密钥

敏感配置已通过 `.gitignore` 隔离。请在 `auradoc-backend/src/main/resources/` 下新建 `application-local.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/auradocs?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: "your-mysql-password"

auradoc:
  jwt:
    secret: "your-jwt-secret-key-at-least-32-characters-long"
  ai:
    base-url: "[https://api.openai.com/v1](https://api.openai.com/v1)"   # 支持任意 OpenAI 兼容接口
    api-key: "your-llm-api-key"
    model: "gpt-4o-mini"
```

### 3. 启动后端服务

在 `auradoc-backend` 目录下运行（默认端口 `8080`）：

```bash
mvn spring-boot:run
```

### 4. 启动前端服务

在 `auradocs-frontend` 目录下安装依赖并启动（默认端口 `5173`，已通过 Vite 代理 `/api` 至 `8080`）：

```bash
npm install
npm run dev
```

启动完成后访问 `http://localhost:5173`。

---

## 核心 API 概览

| 模块 | 方法与路径 | 说明 |
| :--- | :--- | :--- |
| 认证 | `POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me` | 注册、登录、获取当前用户信息 |
| 知识空间 | `GET /api/spaces`、`POST /api/spaces`、`DELETE /api/spaces/{id}` | 空间列表、创建与删除 |
| 知识文档 | `GET /api/docs`、`GET /api/docs/{id}`、`POST/PUT/DELETE /api/docs` | 文档列表、详情与增删改查 |
| 智能检索 | `GET /api/docs/search?spaceId=&keyword=` | 标题、正文、AI 摘要多字段联合搜索 |
| AI 能力 | `POST /api/docs/{id}/summary`、`POST /api/ai/chat/stream` | 一键生成摘要落库、SSE 文档流式问答 |

---

## 后续规划

* 接入向量数据库实现基于 RAG 的跨文档语义检索
* 支持文档历史版本回溯与协作导出（PDF / Markdown）
* 提供 Docker Compose 一键编排部署方案
