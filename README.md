# 智学引擎 · ZhiXue Engine

> 全国高校 AI Web 开发挑战赛参赛作品 —— **AI 原生个性化学习平台**
> 以 AI Agent 为核心，融合 RAG 知识增强、多模态解题与全链路 AI 安全治理。

---

## 一、项目简介

大多数"AI + 教育"产品只是把聊天框塞进页面：模型不知道你的教材、不记得你的薄弱点、也无法把"我该怎么学"落成可执行的任务。

**智学引擎**把 AI 当成系统的内核而不是附件：

| 传统做法 | 智学引擎的做法 |
| --- | --- |
| 单轮问答，答完即忘 | **Agent 多步循环**：自动判断该查资料、出题还是做计划，并在多步工具调用后继续作答 |
| 模型凭记忆编造 | **RAG 检索增强**：基于你上传的讲义切片作答，答案带原文出处与相似度 |
| 只支持文字 | **多模态**：拍照上传题目，图文联合理解；支持语音输入与朗读 |
| 输出不可控 | **安全护栏**：违规内容拦截、提示词注入标记、限流、全链路 AI 审计 |

一句话概括：**让 AI 真正"教得动"——会查资料、会出题、会规划、会复盘。**

---

## 二、核心功能

### 1. AI 导师（Agent 对话）
- SSE 流式输出，**逐字渲染**，首字延迟低
- **思考过程可视化**：实时展示模型的推理流（`reasoning_content`），可折叠
- **Agent 工具链可视化**：检索知识库、生成计划、生成测验、读取学习报告，每次调用都有状态、参数与结果摘要
- 多轮上下文、会话管理、图片提问、语音输入、回答朗读

### 2. RAG 知识问答
- 支持 **PDF / Markdown / TXT** 上传与纯文本笔记
- 句子级切片（480 字符 / 80 字符重叠）→ 稀疏词向量 → MySQL 持久化
- **混合检索**：TF-IDF 余弦相似度（62%）+ 关键词覆盖率（38%）+ 长度惩罚，再用 **MMR** 去冗余
- 内置 4 篇公共示例知识库，开箱即可体验检索效果
- 回答自动附 **引用来源**（文档名 / 段落序号 / 相关度）

### 3. 智能测验
- AI 依据主题 + 知识库资料命题（4 选项单选题，含解析与知识点）
- 在线作答 → 自动批改 → **AI 学情诊断**（自然语言总结 + 改进建议）
- 逐题解析回看、错题知识点自动计入掌握度画像

### 4. 学习计划
- 输入目标 / 水平 / 天数 / 每日投入，AI 拆解为**按天可打卡任务**（含验收标准与知识点）
- 任务勾选打卡，进度环实时更新，完成情况进入学习统计

### 5. 学习画像与数据看板
- 近 30 天学习时长、连续打卡、7 日节奏图、知识点掌握度、最近动态
- AI 使用量统计：调用次数、tokens、平均耗时、拦截次数（透明可追溯）

### 6. AI 安全与可控性
- 输入侧：违法违规内容拦截、**提示词注入检测**（命中则标记并在系统提示词中强制降权）
- 输出侧：高风险话题自动附加免责声明、系统提示词泄露防护
- 前端：AI 输出统一经 **DOMPurify** 净化后渲染，杜绝 XSS
- 治理：每用户 AI 调用滑动窗口限流、全量调用审计落库

---

## 三、技术架构

```
┌─────────────────────────────────────────────────────────────┐
│  Vue 3 + TypeScript SPA（Vite 构建，路由级代码分割）           │
│  Pinia 状态管理 · SSE 流式渲染 · 设计系统（CSS 变量 / 暗色）   │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTPS / JSON / text-event-stream
┌───────────────────────────▼─────────────────────────────────┐
│  Spring Boot 3.5（内嵌 Tomcat，可执行 JAR）                   │
│                                                             │
│  安全层   AuthInterceptor · JWT(HS256) · BCrypt · RateLimit   │
│  对话层   ChatController(SSE) → ChatService → AgentOrchestrator│
│  Agent    ToolRegistry〔检索 / 出题 / 计划 / 学情报告〕        │
│  模型层   DeepSeekClient（流式 + 工具调用 + 多模态 + 降级重试）   │
│  RAG      Tokenizer · TextSplitter · KnowledgeIndex(BM25×Cosine)│
│  领域层   Quiz · Plan · Analytics · Safety                    │
└───────────────────────────┬─────────────────────────────────┘
                            │ JDBC（HikariCP）
┌───────────────────────────▼─────────────────────────────────┐
│  MySQL 8.x（utf8mb4，11 张表，启动自动建表）                   │
└─────────────────────────────────────────────────────────────┘
                            │
                   DeepSeek V4.1（deepseek-flash / v4-pro）
```

### 技术选型说明

| 层 | 选型 | 理由 |
| --- | --- | --- |
| 前端 | Vue 3 `<script setup>` + TypeScript + Pinia + Vue Router | 组件化 + 类型安全；路由懒加载把首屏 JS 控制在 ~120KB（gzip 48KB） |
| 后端 | Spring Boot 3.5 + Spring Data JPA | 生态成熟、事务与连接池开箱即用；可执行 JAR 内嵌 Tomcat，部署零依赖 |
| 数据库 | MySQL 8 | 通用、稳定；切片向量与业务数据同库存储，降低运维复杂度 |
| 模型 | DeepSeek V4.1 | 1M 上下文、原生 `reasoning_content`、支持图像输入与 Function Calling，成本低 |
| 流式 | Spring MVC `SseEmitter` | 无需引入 WebFlux，独立线程池隔离 AI 长任务 |
| 认证 | 自研 JWT + BCrypt | 避免为演示项目引入完整 Security 过滤器链，逻辑可读、易审计 |

---

## 四、快速开始

### 1. 环境要求

| 依赖 | 版本 |
| --- | --- |
| JDK | 21+ |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Node.js（仅前端开发/构建需要） | 18+ |

> 仓库已包含构建后的前端产物 `frontend/dist`，**没有 Node 环境也能直接 `mvn package` 打包运行**。

### 2. 准备数据库

```sql
CREATE DATABASE IF NOT EXISTS zhixue DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
```

表结构会在应用启动时自动执行 [`src/main/resources/db/schema.sql`](src/main/resources/db/schema.sql)（全部语句幂等），无需手动导入。

> **表名空间**：所有表统一使用 `zx_` 前缀（如 `zx_users`、`zx_doc_chunks`），因此即使把本应用装进一个已有其他项目的数据库也不会冲突。

### 3. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`（**该文件已被 `.gitignore` 忽略，不会进入版本库**）：

```properties
DB_URL=jdbc:mysql://127.0.0.1:3306/zhixue?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=your_user
DB_PASSWORD=your_password

DEEPSEEK_API_KEY=sk-xxxxxxxx
DEEPSEEK_MODEL=deepseek-flash
DEEPSEEK_VISION_MODEL=deepseek-flash
DEEPSEEK_REASONING_MODEL=deepseek-v4-pro

JWT_SECRET=please-change-to-a-long-random-string-32-chars
```

也可以直接使用同名系统环境变量（优先级高于 `.env`）。

### 4. 构建与运行

```bash
mvn clean package -DskipTests
java -jar target/zhixue-engine.jar
```

浏览器访问 <http://localhost:8080>，使用体验账号直接进入：

```
账号：demo
密码：demo123456
```

或点击登录页的「**一键体验**」按钮。

### 5. 前端开发模式（可选）

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173，已配置 /api 代理到 8080
npm run build      # 产物输出到 frontend/dist，会被 Maven 打进 JAR
```

### 6. 运行测试

```bash
mvn test           # 14 个单元测试：分词器 / 切片器 / 安全护栏 / JWT
```

---

## 五、目录结构

```
ChuanZhiBei/
├── pom.xml                          # Maven（Spring Boot 3.5）
├── .env.example                     # 环境变量模板（真实 .env 不入库）
├── frontend/                        # Vue 3 + TypeScript 前端
│   ├── src/api/                     # 请求封装 + SSE 解析
│   ├── src/components/              # 设计系统与业务组件
│   ├── src/views/                   # 6 个页面（路由级懒加载）
│   └── dist/                        # 构建产物（随 Maven 打入 JAR）
├── src/main/java/com/zhixue/engine/
│   ├── agent/                       # Agent 编排器 + 工具注册表 + 4 个工具
│   ├── llm/                         # DeepSeek 客户端（流式/工具/多模态/降级）
│   ├── rag/                         # 分词、切片、向量索引、检索、文档解析
│   ├── chat/                        # 会话、消息、SSE 推流
│   ├── quiz/  plan/  analytics/     # 领域模块
│   ├── safety/                      # 内容护栏 + 限流
│   ├── security/                    # JWT、密码、拦截器
│   └── config/  user/  common/      # 配置、用户、通用响应
├── src/main/resources/
│   ├── application.yml
│   ├── db/schema.sql                # 建表脚本（幂等）
│   └── seed/*.md                    # 公共示例知识库
└── docs/                            # 架构 / AI 技术 / API 文档
```

---

## 六、主要 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` `/login` `/demo` | 注册 / 登录 / 体验账号 |
| GET/PUT | `/api/auth/me` | 学习画像读取与更新 |
| POST | `/api/chat/stream` | **SSE 流式对话**（Agent 编排入口） |
| GET | `/api/chat/conversations` | 会话列表（含历史消息） |
| POST | `/api/knowledge/documents` | 上传 PDF/MD/TXT 并向量化 |
| POST | `/api/knowledge/search` | 混合检索，返回引用片段 |
| POST | `/api/quiz/generate` | AI 出题 |
| POST | `/api/quiz/{id}/submit` | 提交答卷 + AI 学情诊断 |
| POST | `/api/plan/generate` | AI 生成学习计划 |
| PATCH | `/api/plan/tasks/{id}` | 任务打卡 |
| GET | `/api/analytics/overview` | 学习数据总览 |
| GET | `/api/analytics/ai-usage` | AI 调用审计记录 |

完整定义见 [`docs/API.md`](docs/API.md)。

---

## 七、AI 技术要点（摘要）

| 考察点 | 本项目实现 |
| --- | --- |
| 大模型集成 | DeepSeek V4.1，分层提示词（角色 / 教学原则 / 工具规范 / 安全边界 / 学生画像），结构化输出强制 JSON + 双模型降级 |
| Agent / 工作流 | `AgentOrchestrator` 多步循环（最多 4 步）：模型决策 → 工具执行 → 结果回填 → 继续生成；工具异常回填给模型自行纠错；步数耗尽强制收尾 |
| RAG | 句子级切片 + 稀疏词向量 + 混合打分（余弦 62% / 关键词覆盖 38%）+ MMR 去冗余 + 引用溯源 |
| 多模态 | 图片以 base64 data URL 直接送入多模态模型；语音输入（Web Speech API）与朗读（SpeechSynthesis） |
| AI 安全 | 违法内容拦截、提示词注入标记、输出免责声明、DOMPurify 净化、限流、审计 |
| 工具集成 | Function Calling 形式的工具注册表，工具定义自动注入模型；新增工具只需实现 `AgentTool` |

详细方案见 [`docs/AI-TECH.md`](docs/AI-TECH.md)。

---

## 八、安全与合规

- **密钥零入库**：所有敏感配置通过环境变量 / `.env` 注入，`.env` 已在 `.gitignore` 中排除
- **密码加盐哈希**：BCrypt（cost 10），数据库不存明文
- **JWT 鉴权**：HS256 签名 + 过期校验，拦截器统一守卫 `/api/**`
- **越权防护**：会话 / 测验 / 计划 / 文档均校验 `userId` 归属
- **内容安全**：输入护栏 + 输出净化 + 提示词注入防护 + 全量审计

---

## 九、团队信息

| 角色 | 姓名 | 分工 |
| --- | --- | --- |
| 队长 / 全栈 | 待填写 | 架构设计、Agent 与 RAG 实现、前端交互 |
| 成员 | 待填写 | 视觉设计、测试与文档 |

> 指导老师：待填写　|　院校：待填写

---

## 十、后续规划

- 接入向量数据库（Milvus / pgvector）替换内存索引，支持十万级切片
- Agent 增加长期记忆（学习事件摘要写入向量库），实现跨会话个性化
- 引入端侧推理（WebGPU + ONNX Runtime Web）做本地敏感信息过滤
- 增加教师端：班级学情看板与作业分发
- 支持 MCP 协议，把外部题库 / 校园教务系统注册为标准工具
