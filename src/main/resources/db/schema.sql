-- ============================================================
-- 智学引擎 (ZhiXue Engine) 数据库结构
-- MySQL 8.0+ / utf8mb4；应用启动时自动执行（全部语句幂等）
--
-- 说明：所有表统一使用 zx_（ZhiXue）前缀，避免与同一数据库中的
--       其他项目表（如公共 users 表）发生命名冲突。
-- ============================================================

CREATE TABLE IF NOT EXISTS zx_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    email         VARCHAR(120) NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'STUDENT',
    school        VARCHAR(128) NULL,
    grade         VARCHAR(32)  NULL,
    major         VARCHAR(64)  NULL,
    learning_goal VARCHAR(255) NULL,
    level         VARCHAR(16)  NOT NULL DEFAULT 'BEGINNER',
    avatar_color  VARCHAR(16)  NOT NULL DEFAULT '#4F46E5',
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户';

CREATE TABLE IF NOT EXISTS zx_conversations (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    title         VARCHAR(128) NOT NULL,
    scene         VARCHAR(32)  NOT NULL DEFAULT 'TUTOR',
    message_count INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_conv_user (user_id, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 会话';

CREATE TABLE IF NOT EXISTS zx_messages (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         MEDIUMTEXT  NULL,
    reasoning       MEDIUMTEXT  NULL,
    tool_trace      MEDIUMTEXT  NULL COMMENT '工具调用轨迹 JSON 数组',
    citations       MEDIUMTEXT  NULL COMMENT 'RAG 引用来源 JSON 数组',
    tokens          INT         NOT NULL DEFAULT 0,
    created_at      DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_msg_conv (conversation_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '会话消息';

CREATE TABLE IF NOT EXISTS zx_documents (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NULL COMMENT 'NULL 表示公共示例知识库',
    title       VARCHAR(200) NOT NULL,
    source_type VARCHAR(16)  NOT NULL COMMENT 'TEXT/MARKDOWN/PDF',
    file_name   VARCHAR(255) NULL,
    byte_size   INT          NOT NULL DEFAULT 0,
    char_count  INT          NOT NULL DEFAULT 0,
    chunk_count INT          NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL DEFAULT 'READY',
    summary     VARCHAR(500) NULL,
    created_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_doc_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '知识库文档';

CREATE TABLE IF NOT EXISTS zx_doc_chunks (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    document_id   BIGINT       NOT NULL,
    seq           INT          NOT NULL,
    content       TEXT         NOT NULL,
    char_count    INT          NOT NULL DEFAULT 0,
    token_count   INT          NOT NULL DEFAULT 0,
    sparse_vector MEDIUMTEXT   NOT NULL COMMENT '稀疏词权重向量 JSON',
    keywords      VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_chunk_doc (document_id, seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '知识库切片（含向量）';

CREATE TABLE IF NOT EXISTS zx_quizzes (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    title          VARCHAR(200) NOT NULL,
    topic          VARCHAR(200) NOT NULL,
    difficulty     VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    question_count INT          NOT NULL DEFAULT 0,
    score          INT          NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at     DATETIME(3)  NOT NULL,
    completed_at   DATETIME(3)  NULL,
    PRIMARY KEY (id),
    KEY idx_quiz_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 生成测验';

CREATE TABLE IF NOT EXISTS zx_quiz_questions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    quiz_id         BIGINT       NOT NULL,
    seq             INT          NOT NULL,
    type            VARCHAR(16)  NOT NULL DEFAULT 'SINGLE',
    stem            TEXT         NOT NULL,
    options         MEDIUMTEXT   NULL COMMENT '选项 JSON [{"key":"A","text":"..."}]',
    answer          VARCHAR(64)  NOT NULL,
    explanation     TEXT         NULL,
    knowledge_point VARCHAR(64)  NULL,
    user_answer     VARCHAR(64)  NULL,
    is_correct      TINYINT      NULL,
    PRIMARY KEY (id),
    KEY idx_qq_quiz (quiz_id, seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '测验题目';

CREATE TABLE IF NOT EXISTS zx_study_plans (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    title         VARCHAR(200) NOT NULL,
    goal          VARCHAR(255) NOT NULL,
    level         VARCHAR(16)  NOT NULL DEFAULT 'BEGINNER',
    duration_days INT          NOT NULL DEFAULT 7,
    daily_minutes INT          NOT NULL DEFAULT 60,
    summary       TEXT         NULL,
    created_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_plan_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 学习计划';

CREATE TABLE IF NOT EXISTS zx_study_tasks (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    plan_id          BIGINT       NOT NULL,
    day_index        INT          NOT NULL DEFAULT 1,
    seq              INT          NOT NULL DEFAULT 1,
    title            VARCHAR(200) NOT NULL,
    detail           TEXT         NULL,
    knowledge_points VARCHAR(255) NULL,
    minutes          INT          NOT NULL DEFAULT 30,
    status           VARCHAR(16)  NOT NULL DEFAULT 'TODO',
    completed_at     DATETIME(3)  NULL,
    PRIMARY KEY (id),
    KEY idx_task_plan (plan_id, day_index, seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '学习计划任务';

CREATE TABLE IF NOT EXISTS zx_learning_events (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    type            VARCHAR(32)   NOT NULL COMMENT 'CHAT/QUIZ/PLAN_TASK/KNOWLEDGE',
    ref_type        VARCHAR(32)   NULL,
    ref_id          BIGINT        NULL,
    knowledge_point VARCHAR(64)   NULL,
    score           DECIMAL(5, 2) NULL,
    minutes         INT           NOT NULL DEFAULT 0,
    detail          VARCHAR(255)  NULL,
    created_at      DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_event_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '学习行为事件';

CREATE TABLE IF NOT EXISTS zx_ai_usage_logs (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NULL,
    scene             VARCHAR(32)  NOT NULL,
    model             VARCHAR(64)  NOT NULL,
    prompt_tokens     INT          NOT NULL DEFAULT 0,
    completion_tokens INT          NOT NULL DEFAULT 0,
    latency_ms        INT          NOT NULL DEFAULT 0,
    status            VARCHAR(16)  NOT NULL DEFAULT 'OK',
    blocked           TINYINT      NOT NULL DEFAULT 0,
    detail            VARCHAR(255) NULL,
    created_at        DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 调用审计日志（可观测性与安全）';
