-- ============================================
-- Multi-Agent Collaborative Office System
-- Database Schema
-- ============================================

CREATE DATABASE IF NOT EXISTS `agent_office` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `agent_office`;

-- ============================================
-- 1. User & Permission Tables
-- ============================================

CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    real_name VARCHAR(50) COMMENT '真实姓名',
    email VARCHAR(100) COMMENT '邮箱',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属租户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_username (username)
) ENGINE=InnoDB COMMENT='系统用户表';

CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码: ADMIN/MANAGER/USER',
    description VARCHAR(200) COMMENT '角色描述',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB COMMENT='角色表';

CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    permission_code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码: agent:register/task:submit/report:view',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB COMMENT='权限表';

CREATE TABLE sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB COMMENT='角色权限关联表';

-- ============================================
-- 2. Agent & Task Tables
-- ============================================

CREATE TABLE agent_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    agent_code VARCHAR(50) NOT NULL UNIQUE COMMENT '智能体唯一编码',
    agent_name VARCHAR(100) NOT NULL COMMENT '智能体名称',
    agent_capability TEXT NOT NULL COMMENT '能力标签JSON',
    config_json TEXT COMMENT 'Agent配置参数JSON: model/temperature/maxTokens等',
    priority INT NOT NULL DEFAULT 100 COMMENT '执行优先级(数值越小优先级越高)',
    max_concurrency INT NOT NULL DEFAULT 3 COMMENT '最大并发执行数',
    avg_cost_time INT DEFAULT 0 COMMENT '历史平均耗时(ms)',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB COMMENT='智能体注册表';

CREATE TABLE agent_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_no VARCHAR(50) NOT NULL UNIQUE COMMENT '任务唯一编号',
    task_name VARCHAR(200) NOT NULL COMMENT '任务名称',
    task_content TEXT COMMENT '任务原始内容',
    coop_mode VARCHAR(30) NOT NULL DEFAULT 'SERIAL' COMMENT '协同模式: PARALLEL/SERIAL/RELAY/CONDITION/LOOP',
    priority INT NOT NULL DEFAULT 100 COMMENT '任务优先级',
    timeout_seconds INT NOT NULL DEFAULT 300 COMMENT '全局超时时间(秒)',
    task_status TINYINT NOT NULL DEFAULT 0 COMMENT '0处理中 1已完成 2失败 3已取消',
    result_summary LONGTEXT COMMENT '任务聚合结果总结',
    submit_user_id BIGINT COMMENT '提交用户ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属租户ID',
    trace_id VARCHAR(50) COMMENT '全链路追踪ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finish_time DATETIME COMMENT '完成时间',
    INDEX idx_task_no (task_no),
    INDEX idx_tenant_status (tenant_id, task_status),
    INDEX idx_trace_id (trace_id)
) ENGINE=InnoDB COMMENT='协同任务主表';

CREATE TABLE agent_task_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_id BIGINT NOT NULL COMMENT '关联主任务ID',
    agent_code VARCHAR(50) COMMENT '执行智能体编码',
    item_content TEXT COMMENT '子任务内容',
    item_result LONGTEXT COMMENT '子任务执行结果(JSON)',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待执行 1执行中 2成功 3失败 4跳过 5等待审批',
    error_msg TEXT COMMENT '异常信息',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    cost_time INT DEFAULT 0 COMMENT '执行耗时(ms)',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属租户ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finish_time DATETIME COMMENT '完成时间',
    INDEX idx_task_id (task_id),
    INDEX idx_agent_code (agent_code),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='子任务执行明细表';

CREATE TABLE agent_stat_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    report_type VARCHAR(20) NOT NULL COMMENT '报表类型: DAILY/WEEKLY/MONTHLY',
    agent_code VARCHAR(50) COMMENT '智能体编码',
    total_task INT NOT NULL DEFAULT 0 COMMENT '总任务数',
    success_task INT NOT NULL DEFAULT 0 COMMENT '成功任务数',
    fail_task INT NOT NULL DEFAULT 0 COMMENT '失败任务数',
    avg_cost_time INT DEFAULT 0 COMMENT '平均执行耗时(ms)',
    prompt_version VARCHAR(20) COMMENT '使用的Prompt版本号',
    report_content LONGTEXT COMMENT 'AI统计复盘内容',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属租户ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_report_type (report_type, tenant_id)
) ENGINE=InnoDB COMMENT='Agent协同统计报表表';

-- ============================================
-- 3. Prompt & Knowledge Base Tables
-- ============================================

CREATE TABLE agent_prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    template_code VARCHAR(100) NOT NULL COMMENT '模板编码',
    template_content LONGTEXT NOT NULL COMMENT '模板内容(含 {{变量}} 占位符)',
    version VARCHAR(20) NOT NULL COMMENT '版本号: v1/v2/v3',
    agent_code VARCHAR(50) NOT NULL COMMENT '绑定的Agent编码',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0草稿 1启用 2已废弃',
    success_rate DECIMAL(5,2) DEFAULT 0.00 COMMENT '该版本任务成功率(%)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_agent_version (agent_code, version)
) ENGINE=InnoDB COMMENT='Prompt模板表';

CREATE TABLE kb_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    doc_name VARCHAR(200) NOT NULL COMMENT '文档名称',
    doc_content LONGTEXT COMMENT '文档原始内容',
    doc_type VARCHAR(50) COMMENT '文档类型: manual/policy/faq/api_doc',
    vector_id VARCHAR(100) COMMENT '向量数据库文档ID(预留)',
    agent_code VARCHAR(50) COMMENT '关联Agent编码(NULL=全局共享)',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属租户ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_tenant_agent (tenant_id, agent_code)
) ENGINE=InnoDB COMMENT='知识库文档表';

CREATE TABLE task_decompose_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    trigger_keywords VARCHAR(500) COMMENT '触发关键词(逗号分隔)',
    sub_tasks_json LONGTEXT COMMENT '预定义子任务列表JSON',
    coop_mode VARCHAR(30) DEFAULT 'PARALLEL' COMMENT '默认协同模式',
    priority INT NOT NULL DEFAULT 100 COMMENT '模板优先级(数值越小越优先匹配)',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    INDEX idx_priority (priority)
) ENGINE=InnoDB COMMENT='任务拆解模板表';

-- ============================================
-- 4. Monitoring & Audit Tables
-- ============================================

CREATE TABLE llm_token_daily_stat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    stat_date DATE NOT NULL COMMENT '统计日期',
    agent_code VARCHAR(50) COMMENT 'Agent编码',
    model VARCHAR(50) COMMENT '模型名称',
    prompt_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输入Token数',
    completion_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输出Token数',
    total_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总Token数',
    estimated_cost DECIMAL(10,4) DEFAULT 0.0000 COMMENT '预估费用(美元)',
    call_count INT NOT NULL DEFAULT 0 COMMENT '调用次数',
    cache_hit_count INT NOT NULL DEFAULT 0 COMMENT '缓存命中次数',
    UNIQUE KEY uk_date_agent_model (stat_date, agent_code, model)
) ENGINE=InnoDB COMMENT='LLM Token日统计表';

CREATE TABLE approval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_item_id BIGINT NOT NULL COMMENT '关联子任务ID',
    approver_id BIGINT COMMENT '审批人用户ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待审批 1已通过 2已驳回 3超时自动处理',
    comment TEXT COMMENT '审批意见',
    agent_output_snapshot LONGTEXT COMMENT '审批时的Agent输出快照',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    resolve_time DATETIME COMMENT '审批完成时间',
    INDEX idx_task_item (task_item_id)
) ENGINE=InnoDB COMMENT='审批记录表';

-- ============================================
-- 5. Seed Data
-- ============================================

-- 默认角色
INSERT INTO sys_role (role_name, role_code, description) VALUES
('系统管理员', 'ADMIN', '全局管理权限'),
('部门主管', 'MANAGER', '本租户管理权限'),
('普通员工', 'USER', '基础使用权限');

-- 权限定义
INSERT INTO sys_permission (permission_code, permission_name) VALUES
('agent:register', 'Agent注册管理'),
('agent:view', 'Agent列表查看'),
('task:submit', '提交协同任务'),
('task:view', '查看任务'),
('task:cancel', '取消任务'),
('task:approve', '审批任务'),
('report:view', '查看报表'),
('report:token', '查看Token消耗'),
('prompt:manage', 'Prompt模板管理'),
('kb:manage', '知识库管理');

-- 管理员拥有全部权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- 部门主管权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission WHERE permission_code IN
('agent:view', 'task:submit', 'task:view', 'task:cancel', 'task:approve', 'report:view');

-- 普通员工权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 3, id FROM sys_permission WHERE permission_code IN
('agent:view', 'task:submit', 'task:view', 'task:cancel');

-- 默认管理员用户 (密码: admin123, BCrypt编码)
INSERT INTO sys_user (username, password, real_name, email, tenant_id, role_id, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', '系统管理员', 'admin@agentoffice.com', 0, 1, 1);

-- 默认任务拆解模板
INSERT INTO task_decompose_template (template_name, trigger_keywords, sub_tasks_json, coop_mode, priority) VALUES
('研发上线质检', '上线,质检,发布,部署', '[{"name":"代码规范审查","content":"检查代码规范和命名约定","capability":"code_review"},{"name":"代码安全扫描","content":"扫描常见安全漏洞和敏感信息泄露","capability":"code_review"},{"name":"文档完整性检查","content":"检查项目文档是否完整","capability":"doc_analysis"},{"name":"变更风险评估","content":"分析本次变更的影响范围和风险等级","capability":"data_report"}]', 'PARALLEL', 1),
('运维工单复盘', '工单,复盘,统计,分析', '[{"name":"工单数据汇总","content":"汇总指定时间范围内的工单数据","capability":"ticket_process"},{"name":"高频问题分析","content":"统计最高频的工单类型和问题","capability":"data_report"},{"name":"生成复盘报告","content":"基于数据分析生成工单复盘报告","capability":"data_report"}]', 'SERIAL', 2);
