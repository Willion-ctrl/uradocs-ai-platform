-- ==========================================================
-- AuraDocs 企业级智能知识库 - MySQL 8.0 初始化脚本
-- 规范：utf8mb4 / InnoDB / 逻辑外键 / 逻辑删除
-- ==========================================================

CREATE DATABASE IF NOT EXISTS auradocs
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE auradocs;

-- ----------------------------------------------------------
-- 1. 系统用户表 (sys_user)
-- ----------------------------------------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
                          id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                          username      VARCHAR(64)  NOT NULL COMMENT '登录用户名（唯一）',
                          password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密后的密码',
                          nickname      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '用户昵称',
                          email         VARCHAR(128) DEFAULT NULL COMMENT '邮箱（唯一）',
                          avatar_url    VARCHAR(255) DEFAULT '' COMMENT '头像地址',
                          role          VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN / USER',
                          status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-禁用',
                          is_deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
                          created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_username (username),
                          UNIQUE KEY uk_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户表';

-- ----------------------------------------------------------
-- 2. 知识库空间表 (kb_space)
-- ----------------------------------------------------------
DROP TABLE IF EXISTS kb_space;
CREATE TABLE kb_space (
                          id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '空间ID',
                          name        VARCHAR(100) NOT NULL COMMENT '知识空间名称',
                          description VARCHAR(500) DEFAULT '' COMMENT '空间简介',
                          owner_id    BIGINT       NOT NULL COMMENT '创建者ID（逻辑关联 sys_user.id）',
                          visibility  TINYINT      NOT NULL DEFAULT 0 COMMENT '可见性：0-仅自己可见 1-团队公开',
                          is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
                          created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (id),
                          KEY idx_owner_id (owner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库空间表';

-- ----------------------------------------------------------
-- 3. 知识库文档表 (kb_doc)
-- ----------------------------------------------------------
DROP TABLE IF EXISTS kb_doc;
CREATE TABLE kb_doc (
                        id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文档ID',
                        space_id    BIGINT       NOT NULL COMMENT '所属空间ID（逻辑关联 kb_space.id）',
                        parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父文档ID（0表示根目录，用于构建文档树）',
                        title       VARCHAR(200) NOT NULL COMMENT '文档标题',
                        content_md  LONGTEXT     COMMENT 'Markdown 格式文档正文',
                        ai_summary  TEXT         COMMENT 'AI 生成的文档摘要缓存',
                        author_id   BIGINT       NOT NULL COMMENT '作者ID（逻辑关联 sys_user.id）',
                        status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-草稿 1-已发布',
                        is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
                        created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        PRIMARY KEY (id),
                        KEY idx_space_parent (space_id, parent_id),
                        KEY idx_author_id (author_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库文档表';

-- ----------------------------------------------------------
-- 4. AI 对话记录表 (ai_chat_log)
-- ----------------------------------------------------------
DROP TABLE IF EXISTS ai_chat_log;
CREATE TABLE ai_chat_log (
                             id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '对话记录ID',
                             session_id  VARCHAR(64) NOT NULL COMMENT '会话分组UUID（用于多轮上下文拉取）',
                             user_id     BIGINT      NOT NULL COMMENT '提问用户ID（逻辑关联 sys_user.id）',
                             doc_id      BIGINT      DEFAULT NULL COMMENT '关联上下文文档ID（逻辑关联 kb_doc.id，NULL为全局对话）',
                             role        VARCHAR(16) NOT NULL COMMENT '消息角色：user / assistant',
                             content     TEXT        NOT NULL COMMENT '对话内容',
                             model_name  VARCHAR(64) DEFAULT '' COMMENT '调用的模型标识（如 deepseek-chat）',
                             token_usage INT         NOT NULL DEFAULT 0 COMMENT '本次消耗 Token 数',
                             is_deleted  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
                             created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             PRIMARY KEY (id),
                             KEY idx_session_created (session_id, created_at),
                             KEY idx_user_doc (user_id, doc_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI对话历史记录表';

-- ----------------------------------------------------------
-- 5. 初始化联调测试数据 (Seed Data)
-- ----------------------------------------------------------
-- 默认管理员账号：admin / 密码占位符（第三步接入 BCrypt 后替换真实密文）
INSERT INTO sys_user (id, username, password_hash, nickname, email, role)
VALUES (1, 'admin', '$2a$10$PlaceholderHashForDevOnly', '系统管理员', 'admin@auradocs.dev', 'ADMIN');

INSERT INTO kb_space (id, name, description, owner_id, visibility)
VALUES (1, '研发技术规范库', '存放团队架构设计、接口规范与新人上手指南', 1, 1);

INSERT INTO kb_doc (id, space_id, parent_id, title, content_md, ai_summary, author_id, status)
VALUES (
           1, 1, 0,
           'AuraDocs 后端开发规范',
           '# AuraDocs 后端规范\n\n1. 所有接口统一返回 Result<T> 结构。\n2. 数据库禁止使用物理外键，统一采用逻辑删除。',
           '本文档规定了 AuraDocs 后端的统一响应格式与数据库逻辑删除规范。',
           1, 1
       );