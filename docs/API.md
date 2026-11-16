# API 文档

所有接口统一前缀 `/api`，除 `/api/auth/**` 与 `/api/public/**` 外均需在请求头携带：

```
Authorization: Bearer <token>
```

统一响应体：

```json
{ "code": 0, "message": "ok", "data": { } }
```

`code = 0` 表示成功；非 0 时 `message` 为可展示的错误文案（400 参数错误 / 401 未登录 / 403 越权 / 404 不存在 / 409 状态冲突 / 502 AI 服务异常）。

---

## 1. 认证与用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/register` | 注册并直接返回 token |
| POST | `/auth/login` | 登录 |
| POST | `/auth/demo` | 体验账号自动登录（不存在时自动创建） |
| GET | `/auth/me` | 当前用户与学习画像 |
| PUT | `/auth/me` | 更新学习画像 |

**注册**

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "zhangsan",
  "password": "123456",
  "displayName": "张三",
  "school": "示范大学",
  "grade": "大二",
  "major": "计算机科学与技术",
  "learningGoal": "两个月掌握数据结构与算法",
  "level": "BEGINNER"
}
```

```json
{
  "code": 0,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": { "id": 2, "username": "zhangsan", "displayName": "张三", "level": "BEGINNER", "avatarColor": "#0EA5E9" }
  }
}
```

---

## 2. 对话（SSE 流式）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/chat/conversations` | 会话列表（按更新时间倒序） |
| POST | `/chat/conversations` | 新建会话 |
| DELETE | `/chat/conversations/{id}` | 删除会话及其消息 |
| GET | `/chat/conversations/{id}/messages` | 历史消息（含思考过程、工具轨迹、引用） |
| POST | `/chat/stream` | **流式对话**（`text/event-stream`） |

**请求**

```http
POST /api/chat/stream
Content-Type: application/json
Accept: text/event-stream

{
  "conversationId": 12,          // null 表示新建会话
  "content": "帮我查一下我的资料里进程调度有哪些算法",
  "images": ["data:image/png;base64,iVBORw0..."],  // 可选，多模态
  "useKnowledge": true           // 是否开启 RAG
}
```

**响应事件流**

```
event: meta
data: {"conversationId":12,"userMessageId":41,"title":"帮我查一下我的资料里进程调度…"}

event: step
data: {"step":1,"maxSteps":4}

event: reasoning
data: {"delta":"用户想检索知识库"}

event: tool_call
data: {"id":"call_0","name":"search_knowledge_base","arguments":"{\"query\":\"进程调度\"}"}

event: tool_result
data: {"id":"call_0","name":"search_knowledge_base","summary":"检索到 3 个片段，来自《操作系统核心概念》","artifact":{"hitCount":3}}

event: citations
data: {"items":[{"index":1,"documentTitle":"操作系统核心概念","seq":5,"snippet":"…","score":0.78}]}

event: delta
data: {"delta":"根据你的资料，进程调度主要有…"}

event: usage
data: {"promptTokens":1860,"completionTokens":420,"latencyMs":6200}

event: done
data: {"conversationId":12,"messageId":42,"cancelled":false}
```

错误事件：`event: error` + `{"message":"..."}`。

---

## 3. 知识库

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/knowledge/documents` | 文档列表 + 统计（含公共示例） |
| POST | `/knowledge/documents` | `multipart/form-data` 上传（`file`、可选 `title`） |
| POST | `/knowledge/documents/text` | 以纯文本新建笔记 |
| DELETE | `/knowledge/documents/{id}` | 删除自己的文档 |
| POST | `/knowledge/search` | 混合检索 |

**检索**

```http
POST /api/knowledge/search
{ "query": "B+ 树为什么适合范围查询", "topK": 5 }
```

```json
{
  "code": 0,
  "data": [
    { "documentTitle": "数据库系统原理与MySQL实践", "seq": 3, "snippet": "B+ 树索引是 InnoDB 的默认索引结构…", "score": 0.82 }
  ]
}
```

---

## 4. 智能测验

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/quiz/generate` | AI 生成测验（会结合知识库） |
| GET | `/quiz` | 测验列表 |
| GET | `/quiz/{id}` | 题目详情（不含答案） |
| POST | `/quiz/{id}/submit` | 提交答卷，返回批改 + 学情诊断 |
| GET | `/quiz/{id}/result` | 回看已提交测验的逐题解析 |

**生成**

```http
POST /api/quiz/generate
{ "topic": "二叉树遍历", "difficulty": "MEDIUM", "count": 5 }
```

`difficulty`：`EASY | MEDIUM | HARD`；`count`：3–10。

**提交**

```http
POST /api/quiz/12/submit
{ "answers": [ { "questionId": 101, "answer": "B" } ] }
```

```json
{
  "code": 0,
  "data": {
    "quizId": 12,
    "score": 80,
    "total": 5,
    "correctCount": 4,
    "results": [
      {
        "questionId": 101,
        "stem": "二叉树的中序遍历顺序是？",
        "options": [{ "key": "A", "text": "根左右" }],
        "correctAnswer": "B",
        "userAnswer": "B",
        "correct": true,
        "explanation": "中序遍历按 左-根-右 访问…",
        "knowledgePoint": "二叉树遍历"
      }
    ],
    "weakPoints": ["红黑树"],
    "diagnosis": "本次答对 4/5 题…"
  }
}
```

---

## 5. 学习计划

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/plan/generate` | AI 生成按天计划 |
| GET | `/plan` | 计划列表（含进度） |
| GET | `/plan/{id}` | 计划详情（按天分组） |
| PATCH | `/plan/tasks/{taskId}` | 任务打卡 / 取消 |

```http
POST /api/plan/generate
{ "goal": "两周内掌握二叉树与图的基础算法", "level": "BEGINNER", "days": 14, "dailyMinutes": 60 }
```

```http
PATCH /api/plan/tasks/88
{ "status": "DONE" }
```

---

## 6. 学习分析

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/analytics/overview` | 学习总览：时长、打卡、掌握度、7 日节奏、AI 统计 |
| GET | `/analytics/ai-usage` | 最近 25 条 AI 调用审计记录 |

```json
{
  "code": 0,
  "data": {
    "stats": { "studyMinutes": 320, "activeDays": 6, "streakDays": 3, "quizzes": 4,
               "avgScore": 78, "documents": 5, "chunks": 42, "aiCalls": 37 },
    "weekly": [{ "date": "2026-11-10", "label": "周一", "minutes": 40, "events": 5 }],
    "mastery": [{ "point": "二叉树遍历", "mastery": 92, "attempts": 5 }],
    "weakPoints": [{ "point": "红黑树", "mastery": 55, "attempts": 2 }],
    "recent": [{ "type": "QUIZ", "title": "完成了一次 AI 测验", "detail": "二叉树 · AI 测验", "at": "2026-11-16T15:20:00" }],
    "ai": { "calls": 37, "tokens": 48210, "blocked": 1 },
    "insight": "「红黑树」的掌握度只有 55%…"
  }
}
```

---

## 7. 公共接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/public/health` | 健康检查 + AI 配置状态（无需鉴权） |

```json
{ "code": 0, "data": { "status": "UP", "app": "智学引擎 ZhiXue Engine", "aiConfigured": true, "model": "deepseek-flash" } }
```
