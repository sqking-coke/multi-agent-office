# multi-agent-office - 学习指南

多 Agent 协同办公系统 —— Java 原生多智能体协同办公 AI 中台。

## 项目简介

摒弃 LangChain/AutoGen 等重量级 AI 框架，**自研 Agent 调度中枢**，实现多智能体分工协作、状态共享、任务分发、自动协同。整合工单处理、代码审查、文档 AI、数据统计、智能答疑五大场景，构建一体化智能办公中台。

### 核心亮点

- **自研多 Agent 调度架构**：混合规则模板 + LLM 实时拆解，7 种协同模式（并行/串行/接力/主从/条件分支/循环迭代/人工审批）
- **插拔式 Agent 集群**：实现 `BizAgent` 接口即可接入，无需修改核心调度代码
- **完整安全体系**：JWT + Spring Security + RBAC + 多租户隔离 + API Key 加密 + Prompt Injection 防护
- **LLM 统一抽象**：OpenAI / Anthropic 多提供商适配，LRU 缓存复用，Token 统计与预算控制
- **全链路可观测**：traceId 串联 + Micrometer + Prometheus + Grafana + 分级告警

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 核心框架 | Spring Boot | 3.5.x |
| 安全框架 | Spring Security + jjwt | 6.x / 0.12.x |
| 数据库 | MySQL + MyBatis-Plus | 8.0 / 3.5.x |
| 事件通信 | Guava EventBus | 33.x |
| 可观测性 | Micrometer + Prometheus | - |
| 工具库 | Hutool / FastJSON2 / Jasypt | 5.8.x / 2.x / 3.x |
| API 文档 | Knife4j (SpringDoc) | 4.5.x |
| AI 调用 | 原生 HTTP Client | - |

## 项目结构

```
src/main/java/com/agentoffice/
├── AgentOfficeApplication.java      # 应用入口
├── agent/
│   ├── BizAgent.java                # Agent 统一接口
│   ├── AgentConfig.java             # Agent 配置模型
│   ├── event/AgentEvent.java        # 7 种事件类型
│   ├── impl/                        # 5 个内置 Agent 实现
│   │   ├── TicketAgent.java         # 工单处理
│   │   ├── CodeReviewAgent.java     # 代码审查
│   │   ├── DocumentAgent.java       # 文档处理
│   │   ├── DataReportAgent.java     # 数据复盘
│   │   └── RagQaAgent.java          # RAG 智能答疑
│   └── registry/AgentRegistry.java  # Agent 注册中心
├── dispatch/
│   ├── DispatchHub.java             # 调度中枢（核心编排器）
│   ├── TaskDecomposer.java          # 混合任务拆解引擎
│   ├── GlobalContext.java           # 全局上下文存储
│   └── strategy/                    # 5 种协同策略
│       ├── ParallelStrategy.java
│       ├── SerialStrategy.java
│       ├── RelayStrategy.java
│       ├── ConditionalStrategy.java
│       └── LoopStrategy.java
├── llm/
│   ├── LLMProvider.java             # LLM 统一接口
│   ├── OpenAIProvider.java          # OpenAI 适配器
│   ├── AnthropicProvider.java       # Anthropic 适配器
│   ├── LLMService.java              # LLM 服务门面
│   └── LLMCache.java                # LRU 结果缓存
├── security/
│   ├── JwtTokenProvider.java        # JWT 签发与校验
│   ├── JwtAuthenticationFilter.java # 认证过滤器
│   ├── SecurityConfig.java          # Spring Security 配置
│   └── TenantContext.java           # 租户上下文 Holder
├── controller/                      # REST API 控制器
│   ├── AuthController.java          # 认证接口
│   ├── TaskController.java          # 任务接口
│   ├── AgentController.java         # Agent 管理接口
│   ├── ReportController.java        # 报表接口
│   ├── PromptController.java        # Prompt 管理接口
│   └── KnowledgeBaseController.java # 知识库接口
├── entity/                          # 13 个实体类
├── mapper/                          # 13 个 Mapper 接口
├── config/                          # 配置类
├── monitor/AlertService.java        # 告警服务
├── schedule/                        # 定时任务
└── util/                            # 工具类
```

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Maven 3.8+

### 1. 初始化数据库

```bash
mysql -u root -p < docs/schema.sql
```

### 2. 配置环境变量

```bash
export OPENAI_API_KEY="sk-xxx"
export ANTHROPIC_API_KEY="sk-ant-xxx"
export JASYPT_ENCRYPTOR_PASSWORD="your-encrypt-password"
```

### 3. 修改 `src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/agent_office?...
    username: root
    password: your_password

jwt:
  secret: YOUR_BASE64_ENCODED_SECRET_KEY_AT_LEAST_256_BITS
```

### 4. 编译运行

```bash
mvn clean package -DskipTests
java -jar target/multi-agent-office-1.0.0-SNAPSHOT.jar
```

### 5. 登录获取 Token

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 6. 提交协同任务

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"taskName":"研发上线质检","taskContent":"对本次发布进行代码审查和文档检查"}'
```

## API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/login` | 用户登录 |
| POST | `/api/v1/auth/refresh` | 刷新 Token |
| POST | `/api/v1/tasks` | 提交协同任务 |
| GET | `/api/v1/tasks` | 分页查询任务列表 |
| GET | `/api/v1/tasks/{id}` | 查询任务详情 |
| DELETE | `/api/v1/tasks/{id}` | 取消任务 |
| POST | `/api/v1/tasks/{itemId}/approve` | 审批通过 |
| POST | `/api/v1/tasks/{itemId}/reject` | 驳回子任务 |
| GET | `/api/v1/agents` | Agent 能力列表 |
| POST | `/api/v1/agents` | 注册 Agent（ADMIN） |
| PUT | `/api/v1/agents/{code}` | 更新 Agent（ADMIN） |
| DELETE | `/api/v1/agents/{code}` | 注销 Agent（ADMIN） |
| GET | `/api/v1/agents/metrics` | Agent 运行指标 |
| GET | `/api/v1/reports` | 复盘报告 |
| GET | `/api/v1/reports/token-usage` | Token 消耗统计 |
| GET | `/api/v1/prompts` | Prompt 模板列表 |
| GET | `/api/v1/knowledge-base` | 知识库文档列表 |

## 监控端点

| 端点 | 说明 |
|------|------|
| `/actuator/health` | 健康检查 |
| `/actuator/prometheus` | Prometheus 指标 |
| `/actuator/metrics` | 全部指标 |
| `/docs` | Knife4j API 文档 |

## 设计文档

详见 [多 Agent 协同办公系统-项目设计文档.md](../多%20Agent%20协同办公系统-项目设计文档.md)
