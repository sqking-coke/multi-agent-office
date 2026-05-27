# multi-agent-office - 学习指南

> **项目地址**：https://github.com/sqking-coke/multi-agent-office <br>
> **适用对象**：Java 后端开发者，具备 Spring Boot 基础，想深入学习 AI Agent 架构设计
---

## 目录

1. [学习路线图](#1-学习路线图)
2. [架构全景解析](#2-架构全景解析)
3. [核心模块深度剖析](#3-核心模块深度剖析)
   - [3.1 Agent 注册中心](#31-agent-注册中心)
   - [3.2 调度中枢 DispatchHub](#32-调度中枢-dispatchhub)
   - [3.3 任务拆解引擎 TaskDecomposer](#33-任务拆解引擎-taskdecomposer)
   - [3.4 五种协同策略](#34-五种协同策略)
   - [3.5 全局上下文 GlobalContext](#35-全局上下文-globalcontext)
   - [3.6 EventBus 事件驱动](#36-eventbus-事件驱动)
   - [3.7 LLM 调用层](#37-llm-调用层)
   - [3.8 安全与多租户](#38-安全与多租户)
   - [3.9 五大内置 Agent](#39-五大内置-agent)
4. [关键设计模式](#4-关键设计模式)
5. [一次完整任务的生命周期](#5-一次完整任务的生命周期)
6. [动手实践](#6-动手实践)
7. [扩展挑战](#7-扩展挑战)

---

## 1. 学习路线图

### 第一阶段：理解架构

**目标**：理解项目整体分层、模块职责、数据流向。

| 步骤 | 内容 | 关键文件 |
|------|------|---------|
| 1.1 | 阅读项目六层架构 | 本文档第 2 节 |
| 1.2 | 理解核心接口 `BizAgent` | `agent/BizAgent.java` |
| 1.3 | 理解 Agent 如何注册 | `agent/registry/AgentRegistry.java` |
| 1.4 | 跟踪一次任务提交全流程 | `controller/TaskController.java` → `service/TaskService.java` → `dispatch/DispatchHub.java` |
| 1.5 | 运行项目，用 curl 提交任务 | 见 README 快速开始 |

**检查点**：能画出任务从提交到完成的完整调用链。

### 第二阶段：深入核心

**目标**：理解调度中枢、拆解引擎、协同策略的实现细节。

| 步骤 | 内容 | 关键文件 |
|------|------|---------|
| 2.1 | 拆解引擎的混合策略（模板 + LLM） | `dispatch/TaskDecomposer.java` |
| 2.2 | 五种协同策略逐一研读 | `dispatch/strategy/*.java` |
| 2.3 | 全局上下文的设计与并发控制 | `dispatch/GlobalContext.java` |
| 2.4 | EventBus 事件类型与订阅关系 | `agent/event/AgentEvent.java`, `dispatch/event/*.java` |
| 2.5 | LLM 调用链：Service → Provider → Cache | `llm/LLMService.java`, `llm/OpenAIProvider.java`, `llm/LLMCache.java` |

**检查点**：能解释 DispatchHub.submitTask() 方法的每一行代码。

### 第三阶段：安全与工程化

**目标**：理解安全体系、多租户、可观测性。

| 步骤 | 内容 | 关键文件 |
|------|------|---------|
| 3.1 | JWT 认证完整链路 | `security/JwtTokenProvider.java`, `security/JwtAuthenticationFilter.java` |
| 3.2 | RBAC 权限模型 + `@PreAuthorize` | `security/SecurityConfig.java` |
| 3.3 | 多租户数据隔离（ThreadLocal + SQL 拦截器） | `security/TenantContext.java`, `config/MybatisPlusConfig.java` |
| 3.4 | 线程池隔离与告警 | `config/ThreadPoolConfig.java`, `monitor/AlertService.java` |
| 3.5 | Prompt 模板变量解析 | `prompt/PromptResolver.java` |

**检查点**：能解释一个请求从 HTTP 到数据库的完整权限校验链路。

### 第四阶段：扩展实战

**目标**：新增一个自定义 Agent，或新增一种协同策略。

| 步骤 | 内容 |
|------|------|
| 4.1 | 实现 `BizAgent` 接口，新增一个 Agent（如翻译 Agent） |
| 4.2 | 为新增 Agent 配置能力标签、Prompt 模板、拆解模板 |
| 4.3 | 新增一种协同策略（如投票策略 VotingStrategy） |
| 4.4 | 编写单元测试验证协同逻辑 |

---

## 2. 架构全景解析

### 2.1 六层架构图

```
┌─────────────────────────────────────────────────────┐
│                    接入层 (Controller)                │
│  AuthController  TaskController  AgentController     │
│  ReportController  PromptController  KBController    │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────┴─────────────────────────────┐
│                 安全与认证层 (Security)               │
│  JwtAuthFilter → JwtTokenProvider → RBAC → Tenant   │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────┴─────────────────────────────┐
│             多 Agent 调度中枢层 (Dispatch)             │
│  DispatchHub ─ TaskDecomposer ─ GlobalContext        │
│       │              │              │                │
│  ParallelStrategy  SerialStrategy  RelayStrategy     │
│  ConditionalStrategy  LoopStrategy                   │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────┴─────────────────────────────┐
│              智能体集群层 (Agent 实现)                 │
│  TicketAgent  CodeReviewAgent  DocumentAgent         │
│  DataReportAgent  RagQaAgent                         │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────┴─────────────────────────────┐
│               通用工具能力层 (LLM / Prompt)           │
│  LLMService ─ OpenAIProvider ─ AnthropicProvider     │
│  LLMCache ─ PromptResolver                          │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────┴─────────────────────────────┐
│       基础服务层 (Config / Monitor / Schedule)        │
│  MybatisPlusConfig  ThreadPoolConfig  EventBusConfig │
│  AlertService  JasyptConfig  MetricsConfig           │
└─────────────────────────────────────────────────────┘
```

### 2.2 核心设计原则

**中心化调度，去中心化执行**：DispatchHub 统一接收、拆解、分发任务，各 Agent 独立自治执行，互不直接通信。Agent 之间通过 EventBus 异步事件 + GlobalContext 共享数据实现松耦合协同。

**关键约束**：
- Agent 之间**不直接通信**，所有协调经 DispatchHub 中转
- 每个 Agent 的 `execute()` 方法必须**线程安全**（可被多线程并发调用）
- 全局上下文的**生命周期与主任务绑定**，任务完成后归档

---

## 3. 核心模块深度剖析

### 3.1 Agent 注册中心

**文件**：`agent/registry/AgentRegistry.java`

**数据结构**：
```java
ConcurrentHashMap<String, BizAgent> agents;      // agentCode → 实例
ConcurrentHashMap<String, AgentConfig> configs;   // agentCode → 配置
```

**注册时机**：每个 Agent 在 `@PostConstruct` 方法中自注册：
```java
@PostConstruct
public void init() {
    AgentConfig config = AgentConfig.builder()
            .agentCode("code_review_agent")
            .capability(...)
            .priority(10)
            .build();
    registry.register(this, config);
}
```

**Agent 匹配算法**（`findBestForCapability`）：
1. 筛选能力标签包含目标 capability 的 Agent
2. 按 `priority` 升序排列（数值越小越优先）
3. 返回最优匹配（未来可扩展为负载最低优先）

**关键设计点**：
- `AgentConfig` 与 `BizAgent` 分离存储，配置可由 ADMIN 运行时修改而不影响 Agent 实例
- 能力标签使用结构化 `AgentCapability` 对象（`List<String> capabilities`），支持多标签匹配
- 高优先级的 Agent 会被优先选中执行任务

### 3.2 调度中枢 DispatchHub

**文件**：`dispatch/DispatchHub.java`

这是整个系统的核心编排器，`submitTask()` 方法是理解系统的关键入口。

**submitTask 执行流程**：

```
submitTask(taskName, taskContent, userId, tenantId)
  │
  ├── 1. 生成 traceId (UUID 前 8 位)
  ├── 2. 创建 AgentTask 主记录，写入 DB
  ├── 3. 初始化 GlobalContext
  │
  ├── 4. TaskDecomposer.decompose() 拆解任务
  │     ├── 命中模板 → 返回预定义子任务列表 + 协同模式
  │     └── 未命中 → 降级为单任务 SERIAL 模式
  │
  ├── 5. 发布 TaskDecomposedEvent
  ├── 6. 持久化子任务 (AgentTaskItem)
  │
  ├── 7. 选择 CollaborationStrategy 执行
  │     strategies.get(coopMode)
  │
  ├── 8. 更新子任务结果到 DB
  ├── 9. aggregateResults() 生成 Markdown 报告
  └── 10. 更新主任务状态，返回
```

**源码中的 5 步编排**（对应代码行 55-122）：
- **Step 1-2**：traceId + 主任务持久化 + 全局上下文初始化
- **Step 3**：`decomposer.decompose()` 拆解
- **Step 4**：`createSubTaskItems()` 持久化子任务 + 发布事件
- **Step 5**：选择策略执行 `strategy.execute()`
- **Step 6-8**：结果写回 + 聚合 + 终态判定

**自定义线程池**（第 41-45 行）：
```java
new ThreadPoolExecutor(
    10, 50, 60, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(200),
    r -> new Thread(r, "agent-worker"),
    new ThreadPoolExecutor.CallerRunsPolicy());
```
- 核心 10 线程，最大 50 线程
- 有界队列 200，防止无限堆积
- CallerRunsPolicy：队列满时由调用线程执行，提供背压

**最终状态判定**（`determineFinalStatus`）：
- 全部成功 → COMPLETED (1)
- 全部失败 → FAILED (2)
- 部分成功 → COMPLETED (1)（业务上视为部分完成）

### 3.3 任务拆解引擎 TaskDecomposer

**文件**：`dispatch/TaskDecomposer.java`

**混合拆解策略**：

```
decompose(taskName, taskContent)
  │
  ├── Layer 1: 规则模板匹配（优先）
  │     从 task_decompose_template 表查询启用模板
  │     按 priority 升序排列
  │     关键词匹配：taskName + taskContent 包含 trigger_keywords 中任意词
  │     命中 → buildFromTemplate()
  │
  └── Layer 2: 降级兜底
        未命中 → buildDefaultDecomposition()
        返回单子任务 + SERIAL 模式
```

**模板 JSON 结构示例**（`task_decompose_template.sub_tasks_json`）：
```json
[
  {"name": "代码规范审查", "content": "...", "capability": "code_review"},
  {"name": "文档完整性检查", "content": "...", "capability": "doc_analysis"},
  {"name": "数据复盘统计", "content": "...", "capability": "data_report"}
]
```

**LLM 拆解（预留，`decomposeWithLlm`）**：
- 需要 LLM 返回结构化 JSON：`{"subTasks": [...], "coopMode": "PARALLEL"}`
- 校验规则：
  - 子任务数量 1-20
  - 每个子任务的 `requiredCapability` 必须存在于 AgentRegistry
  - 依赖关系无循环（DAG 校验）
- 校验失败 → 降级为 default 拆解

**三层兜底机制**：
1. 模板匹配 → 2. LLM 拆解 → 3. 默认单任务拆解

### 3.4 五种协同策略

所有策略实现 `CollaborationStrategy` 接口，返回 `Map<Integer, AgentResult>`（key 为子任务索引）。

#### 3.4.1 ParallelStrategy（并行）

**文件**：`dispatch/strategy/ParallelStrategy.java`

```
CountDownLatch(n)  ← 等待全部完成
  ├── Thread 1: Agent A 执行子任务 1
  ├── Thread 2: Agent B 执行子任务 2
  └── Thread N: Agent X 执行子任务 N
latch.await(5, MINUTES)  → 汇总结果
```

**适用场景**：无依赖的多项独立检查（如同时代码审查 + 文档审查）。

**超时控制**：`CountDownLatch.await(5, TimeUnit.MINUTES)`，5 分钟后强制返回已收集的结果。

#### 3.4.2 SerialStrategy（串行依赖）

**文件**：`dispatch/strategy/SerialStrategy.java`

```
Step 1: Agent A 执行 → 结果写入 ctx["previousResult"]
Step 2: Agent B 读取 ctx["previousResult"] → 执行
Step 3: Agent C 读取上一步结果 → 执行
任一步失败 → 中止整个链
```

**关键代码**（第 43-44 行）：
```java
if (i > 0 && results.containsKey(i - 1)) {
    ctx.put("previousResult", results.get(i - 1).getData());
}
```

**适用场景**：先分析后报告、先审查后修复等有严格顺序依赖的任务。

#### 3.4.3 RelayStrategy（多轮接力）

**文件**：`dispatch/strategy/RelayStrategy.java`

```
Agent A 输出 summary → 作为 Agent B 的 input
Agent B 输出 summary → 作为 Agent C 的 input
...
```

与 SerialStrategy 的区别：Relay 将**上一个 Agent 的输出直接作为下一个 Agent 的输入内容**（而非通过 context 间接传递），适合链式处理流水线。

**关键代码**（第 50 行）：
```java
input = r.getSummary() != null ? r.getSummary() : Objects.toString(r.getData(), "");
```

**适用场景**：文档解析 → 内容总结 → 格式润色。

#### 3.4.4 ConditionalStrategy（条件分支）

**文件**：`dispatch/strategy/ConditionalStrategy.java`

```
Agent A 执行（条件评估器）
  │
  ├── score >= 70 → 走 Agent B（true 分支，索引 1）
  └── score < 70  → 走 Agent C（false 分支，索引 2）
```

**条件评估逻辑**（`evaluateCondition`）：
- 如果 Agent A 返回的 data 包含 `score` 字段且 ≥ 70 → 条件满足
- 否则以 `result.isSuccess()` 作为条件

**适用场景**：代码评分 > 70 跳过重构，否则进入重构流程。

#### 3.4.5 LoopStrategy（循环迭代）

**文件**：`dispatch/strategy/LoopStrategy.java`

```
for round in 1..MAX_ROUNDS(5):
    Reviewer Agent 审查 → 评分
    if 评分 >= 80: break（收敛）
    Fixer Agent 修复 → 新内容
    if 连续两轮改进 < 1.0: break（收益递减）
```

**三种退出条件**：
1. **质量达标**：`score >= QUALITY_THRESHOLD (80)`
2. **达到最大轮次**：`round >= MAX_ROUNDS (5)`
3. **收益递减**：连续两轮评分改进 < 1.0 分

**适用场景**：审查→修改→再审查，直到评分达标。

### 3.5 全局上下文 GlobalContext

**文件**：`dispatch/GlobalContext.java`

**数据结构**：
```java
ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> store;
//               ↑ taskId              ↑ key       ↑ value

ConcurrentHashMap<String, ReentrantReadWriteLock> locks;
//               ↑ taskId → 每任务独立读写锁
```

**并发控制**：
- `ReentrantReadWriteLock`，读多写少场景优化
- 写操作使用 `writeLock`，读操作使用 `readLock`
- 每个 taskId 拥有独立的锁，不同任务之间无锁竞争

**生命周期管理**：
- `initTask(taskId)` → 创建 taskId 对应的子 Map + 时间戳
- `removeTask(taskId)` → 任务完成后清理
- `evictExpired()` → 清理超过 24 小时未活动的上下文（可由定时任务调用）

**典型使用场景**：
```java
// Agent A 写入中间结果
globalContext.put(traceId, "parsedDocument", documentStructure);

// Agent B 读取 Agent A 的结果
DocStructure doc = globalContext.get(traceId, "parsedDocument");
```

### 3.6 EventBus 事件驱动

**文件**：`config/EventBusConfig.java`, `agent/event/AgentEvent.java`

**事件总线配置**：
```java
AsyncEventBus("agent-event-bus", Executors.newFixedThreadPool(4))
// 4 个守护线程，异步非阻塞
```

**7 种事件类型**（`AgentEvent` 内部类）：

| 事件类 | 发布者 | 消费者 | 携带数据 |
|--------|--------|--------|---------|
| `TaskDecomposed` | DispatchHub | 监控/日志 | 子任务列表、协同模式 |
| `TaskDispatched` | DispatchHub | 目标 Agent、监控 | 目标 Agent 编码 |
| `TaskCompleted` | DispatchHub(结果写回时) | 监控/日志 | 子任务 ID、结果摘要 |
| `TaskFailed` | DispatchHub(结果写回时) | 告警/日志 | 子任务 ID、错误信息 |
| `ContextUpdated` | DispatchHub | 所有 Agent | 变更的 key |
| `ApprovalRequired` | DispatchHub | 通知服务 | 子任务 ID、Agent 编码 |
| `ApprovalResolved` | DispatchHub | 阻塞的 Agent | 审批结果 |

**事件监听器**：
- `dispatch/event/TaskEventListener.java` — 监听任务生命周期事件，记录日志
- `dispatch/event/ContextEventListener.java` — 监听上下文变更
- `dispatch/event/ApprovalEventListener.java` — 监听审批事件

**设计原则**：
- Agent 之间不直接通信，所有事件经 DispatchHub 统一中转
- 事件为异步，不阻塞 Agent 主流程
- EventBus 异常不影响主流程（Guava EventBus 内置异常隔离）

### 3.7 LLM 调用层

#### 3.7.1 统一接口

**文件**：`llm/LLMProvider.java`

```java
public interface LLMProvider {
    String completion(systemPrompt, userPrompt, model, params);
    Flux<String> completionStream(systemPrompt, userPrompt, model, params);
    List<Float> embed(text, model);
    String getProviderName();
}
```

#### 3.7.2 LLMService 门面

**文件**：`llm/LLMService.java`

**调用流程**：
```
completion(systemPrompt, userPrompt, model, agentCode)
  │
  ├── 1. Token 预算检查 (TokenBudgetService)
  │     预算 >= 100% → 熔断，返回兜底 JSON
  │
  ├── 2. 提供商路由 (resolveProvider)
  │     model 含 "claude" → AnthropicProvider
  │     model 含 "deepseek" → DeepSeekProvider(OpenAI 兼容)
  │     default → OpenAIProvider
  │
  ├── 3. 缓存查询 (LLMCache)
  │     key = MD5(systemPrompt ||| userPrompt ||| model)
  │     命中 → 返回缓存 + 记录 cache_hit 统计
  │
  ├── 4. 实际 LLM 调用
  │
  ├── 5. 结果写入缓存
  └── 6. 记录 Token 消耗统计
```

**Token 统计**（`recordTokenStat`）：
- 按 `日期 + agentCode + model` 唯一确定一条统计记录
- 新建或累加当日数据：promptTokens、completionTokens、callCount、cacheHitCount
- 成本估算：按模型定价计算（GPT ≈ $0.03/1K prompt + $0.06/1K completion）

#### 3.7.3 LLMCache

**文件**：`llm/LLMCache.java`

- **数据结构**：`LinkedHashMap<>(maxSize, 0.75f, true)`（access-order = true 实现 LRU）
- **缓存 Key**：`MD5(systemPrompt + "|||" + userPrompt + "|||" + model)`
- **TTL**：可配置，默认 3600 秒
- **淘汰策略**：超过 maxSize 时淘汰最久未访问条目 + TTL 过期淘汰
- **线程安全**：所有 public 方法使用 `synchronized`

#### 3.7.4 预算控制（TokenBudgetService）

```
dailyTokenBudget (Agent 配置中的 dailyTokenBudget 字段)
  │
  ├── < 80%: 正常调用
  ├── 80% ~ 99%: 正常调用 + 发送 P2 预警告警
  └── >= 100%: 熔断 + 返回兜底 JSON + 发送 P1 严重告警
```

### 3.8 安全与多租户

#### 3.8.1 认证链路

```
HTTP Request (Authorization: Bearer <jwt>)
  → JwtAuthenticationFilter.extractToken() 提取 Token
  → JwtTokenProvider.validateToken() 校验签名与有效期
  → JwtTokenProvider.parseToken() 解析 userId/tenantId/roleCode
  → TenantContext.setTenantId() / setUserId() 注入 ThreadLocal
  → SecurityContextHolder 设置 Authentication
  → Controller 层可访问 @PreAuthorize 注解控制的方法
  → finally: TenantContext.clear() 清理
```

**JWT Payload**：
```json
{
  "sub": "1",             // userId
  "tenantId": 100,
  "roleCode": "ADMIN",
  "permissions": ["agent:register", "task:submit", "report:view"],
  "iat": 1719000000,
  "exp": 1719086400
}
```

#### 3.8.2 RBAC 权限模型

**三级授权**：用户 → 角色 → 权限

| 角色 | roleCode | 典型权限 |
|------|----------|---------|
| 系统管理员 | ADMIN | Agent 注册/删除、Prompt 管理、全局配置 |
| 部门主管 | MANAGER | 任务审批、报表查看、知识库管理 |
| 普通员工 | USER | 任务提交/查看、Agent 列表查看 |

**权限控制粒度**：
- **接口级**：`SecurityConfig.filterChain()` 中按 URL pattern + HTTP method 配置
- **数据级**：MyBatis-Plus TenantLineInnerInterceptor 自动追加 `tenant_id` 条件

#### 3.8.3 多租户数据隔离

**文件**：`config/MybatisPlusConfig.java`

```
TenantContext.getTenantId()  ← ThreadLocal，由 JWT Filter 注入
       │
       ▼
TenantLineInnerInterceptor
       │
       ├── SELECT → 自动追加 WHERE tenant_id = ?
       ├── INSERT → 自动填充 tenant_id 字段
       ├── UPDATE → 自动追加 WHERE tenant_id = ?
       └── DELETE → 自动追加 WHERE tenant_id = ?
```

**排除表**（不进行租户隔离）：`sys_role`, `sys_permission`, `sys_role_permission`, `task_decompose_template`, `agent_info`, `agent_prompt_template`, `approval_record`, `llm_token_daily_stat`

#### 3.8.4 Prompt Injection 防护

在 `PromptResolver.resolve()` 中，对用户输入变量进行以下处理：
- 使用 `Matcher.quoteReplacement()` 转义特殊字符
- 变量按命名空间隔离（`task.*`, `global.*`, `agent.config.*`），防止越权读取
- LLM 输出的 JSON 做格式校验，解析失败时使用 fallback 对象（如 `parseResult()` 中的兜底）

### 3.9 五大内置 Agent

所有 Agent 遵循统一模式：

```
@PostConstruct init():
  1. 构建 AgentConfig (能力标签、模型、温度、Token 预算等)
  2. registry.register(this, config)

execute(itemId, content, globalContext):
  1. 记录开始时间
  2. promptResolver.resolve() 解析 Prompt 模板变量
  3. llmService.completion() 调用 LLM
  4. parseResult() 解析 LLM 返回的 JSON
  5. 构建 AgentResult 返回 (success + summary + data + costTimeMs)
```

#### CodeReviewAgent（代码审查）

- **能力标签**：`code_review`, `bug_detection`, `security_scan`
- **System Prompt**：四维度审查（规范/BUG/性能/安全）
- **输出**：`{"score": 85, "issues": [...], "summary": "..."}`
- **语言检测**：`detectLanguage()` 基于关键词启发式检测 Java/Python/JS/Go
- **JSON 解析**：`parseResult()` 处理 LLM 可能包裹的 Markdown 代码块

#### TicketAgent（工单处理）

- **能力标签**：`ticket_process`
- **System Prompt**：工单分类（技术/业务/行政/其他）、紧急程度 1-5、处理建议、自动回复
- **输出**：`{"category": "技术类", "urgency": 4, "suggestion": "...", "autoReply": "..."}`

#### DocumentAgent（文档处理）

- **能力标签**：`doc_analysis`, `doc_summary`, `doc_rewrite`
- **操作类型**：从 `globalContext["docOperation"]` 推断，支持总结/提取要点/改写/规整格式
- **输出**：`{"operation": "总结", "result": "...", "keyPoints": [...]}`

#### DataReportAgent（数据复盘）

- **能力标签**：`data_report`, `data_summary`, `trend_analysis`
- **System Prompt**：汇总指标、识别趋势、异常检测、优化建议
- **输出**：`{"summary": "...", "metrics": {...}, "trends": [...], "anomalies": [...], "recommendations": [...]}`

#### RagQaAgent（智能答疑）

- **能力标签**：`rag_qa`, `knowledge_search`
- **前置条件**：`kb_loaded`（知识库已加载）
- **知识检索**：`retrieveKnowledge()` 从 `kb_document` 表关键词匹配 TOP 5 文档
- **输出**：`{"answer": "...", "confidence": 0.9, "sources": [...], "relatedQuestions": [...]}`
- **注意**：当前使用简单关键词匹配，生产环境应改为向量相似度检索（Milvus/Chroma）

---

## 4. 关键设计模式

### 4.1 策略模式（Strategy Pattern）

**位置**：`dispatch/strategy/CollaborationStrategy` + 5 种实现

```
CollaborationStrategy (接口)
  ├── ParallelStrategy
  ├── SerialStrategy
  ├── RelayStrategy
  ├── ConditionalStrategy
  └── LoopStrategy

DispatchHub 中：
strategies.put(strategy.getMode(), strategy);  // 注册
strategies.get(coopMode).execute(...);          // 运行时选择
```

**为什么用策略模式**：新增协同模式只需添加一个实现类并注册，DispatchHub 无需修改。

### 4.2 门面模式（Facade Pattern）

**位置**：`llm/LLMService`

对外暴露统一接口 `completion()`，内部处理：
- Token 预算检查 → 缓存查询 → 提供商路由 → 实际调用 → 统计记录

调用方（Agent）无需关心底层细节。

### 4.3 观察者模式（Observer Pattern）

**位置**：Guava EventBus 事件系统

DispatchHub 发布事件（`eventBus.post()`），多个订阅者异步响应。发布者不知道订阅者是谁。

### 4.4 注册表模式（Registry Pattern）

**位置**：`agent/registry/AgentRegistry`

```java
ConcurrentHashMap<String, BizAgent> agents;
ConcurrentHashMap<String, AgentConfig> configs;
```

Agent 自注册，DispatchHub 按能力标签查找。解耦 Agent 实现与调度逻辑。

### 4.5 模板方法模式（Template Method）

**位置**：所有 Agent 的 `execute()` 方法

每个 Agent 遵循相同的执行骨架：
```
1. 记录开始时间
2. 解析 Prompt 变量
3. 调用 LLM
4. 解析结果 JSON
5. 构建 AgentResult（含耗时）
```
但每个 Agent 的 System Prompt、输出格式、解析逻辑各有不同。

### 4.6 ThreadLocal 模式

**位置**：`security/TenantContext`

```java
ThreadLocal<Long> TENANT_HOLDER  // 租户 ID
ThreadLocal<Long> USER_HOLDER    // 用户 ID
```

请求进入时由 Filter 设置，请求结束时 `finally` 清理。确保同一请求线程内任意位置可获取当前用户信息。

---

## 5. 一次完整任务的生命周期

以"研发上线质检"为例，跟踪完整调用链：

```
1. POST /api/v1/tasks  {"taskName": "研发上线质检", "taskContent": "对 v2.3.0 进行上线前检查"}
   │
2. JwtAuthenticationFilter 提取 Token → TenantContext.setUserId(1)
   │
3. TaskController.submit() → TaskService.submit() → DispatchHub.submitTask()
   │
4. traceId = "a1b2c3d4"
   AgentTask 写入 DB: id=101, task_no="AT1719000000", trace_id="a1b2c3d4"
   │
5. GlobalContext.initTask("a1b2c3d4")
   │
6. TaskDecomposer.decompose("研发上线质检", "对 v2.3.0 进行上线前检查")
   关键词匹配: "质检" → 命中模板 "研发上线质检模板"
   DecomposeResult:
     coopMode = "PARALLEL"
     subTasks = [
       {name: "代码规范审查", capability: "code_review"},
       {name: "文档完整性检查", capability: "doc_analysis"},
       {name: "变更风险评估", capability: "data_report"}
     ]
   │
7. eventBus.post(TaskDecomposed(traceId, 101, [...], "PARALLEL"))
   │
8. 3 条 AgentTaskItem 写入 DB (status=PENDING)
   eventBus.post(TaskDispatched(...)) × 3
   │
9. ParallelStrategy.execute()
   CountDownLatch(3)
   ThreadPoolExecutor.submit() × 3:
     Thread-1: CodeReviewAgent.execute("a1b2c3d4-0", "检查代码规范...")
     Thread-2: DocumentAgent.execute("a1b2c3d4-1", "检查文档...")
     Thread-3: DataReportAgent.execute("a1b2c3d4-2", "评估变更风险...")
   │
10. 各 Agent 内部:
    promptResolver.resolve() → llmService.completion() → parseResult()
    → AgentResult(success=true, summary="评分: 85/100", data={score:85,...})
   │
11. CountDownLatch 归零，ParallelStrategy 返回 Map<Integer, AgentResult>
   │
12. updateSubTaskResults(): 写回 AgentTaskItem (status=SUCCESS/FAILED)
    发布 TaskCompletedEvent 或 TaskFailedEvent
   │
13. aggregateResults(): 生成 Markdown 协同执行报告
    ```
    ## 研发上线质检 - 协同执行报告
    **协同模式**: PARALLEL
    **执行结果**: 3/3 成功

    ### 1. 代码规范审查
    - 状态: 成功
    - 摘要: 评分: 85/100, 错误: 0, 警告: 3
    ...
    ```
   │
14. 主任务状态更新: task_status=1(COMPLETED), result_summary=<Markdown报告>
   │
15. 返回 AgentTask JSON 给客户端
```

---

## 6. 动手实践

### 练习 1：追踪一次完整调用链

**目标**：在 IDE 中设置断点，提交一个任务，逐步跟踪代码执行。

**步骤**：
1. 在 `DispatchHub.submitTask()` 方法入口设置断点
2. 在 `TaskDecomposer.decompose()` 设置断点
3. 在 `ParallelStrategy.execute()` 设置断点
4. 在 `CodeReviewAgent.execute()` 设置断点
5. 提交一个任务：`curl -X POST localhost:8080/api/v1/tasks -H "Authorization: Bearer <token>" -d '{"taskName":"研发上线质检","taskContent":"检查代码和文档"}'`
6. 观察每个断点处的变量值：traceId、拆解结果、Agent 匹配、执行结果

### 练习 2：新增拆解模板

**目标**：为"周报汇总"场景新增一个拆解模板。

**步骤**：
1. 在 `task_decompose_template` 表插入记录：
   ```sql
   INSERT INTO task_decompose_template (template_name, trigger_keywords, sub_tasks_json, coop_mode, priority, status)
   VALUES ('周报汇总模板', '周报,汇总,总结',
   '[{"name":"数据统计","content":"统计本周各项数据指标","capability":"data_report"},
     {"name":"文档汇总","content":"汇总各团队文档","capability":"doc_analysis"}]',
   'PARALLEL', 10, 1);
   ```
2. 提交任务，验证模板命中：
   ```bash
   curl -X POST localhost:8080/api/v1/tasks -d '{"taskName":"本周工作周报","taskContent":"汇总本周研发数据和工作文档"}'
   ```
3. 观察日志中的 `Template matched: 周报汇总模板`

### 练习 3：实现一个翻译 Agent

**目标**：新增一个 `TranslationAgent`，实现中英文翻译。

**关键步骤**：
1. 创建 `agent/impl/TranslationAgent.java`，实现 `BizAgent`
2. 在 `@PostConstruct` 中注册到 AgentRegistry
3. 能力标签：`["translation", "zh_en", "en_zh"]`
4. System Prompt 要求 LLM 返回 `{"sourceLanguage": "...", "targetLanguage": "...", "translatedText": "..."}`
5. 在 `task_decompose_template` 中新增模板，trigger_keywords 包含 "翻译"
6. 提交一个翻译任务验证

### 练习 4：分析 LLM 调用缓存效果

**目标**：验证 LRU 缓存是否生效。

**步骤**：
1. 在 `LLMCache.get()` 方法中设置断点或添加日志
2. 连续两次提交相同内容的任务
3. 观察第二次调用时 `Cache hit: xxx` 日志
4. 检查 `llm_token_daily_stat` 表中 `cache_hit_count` 字段的变化

### 练习 5：配置 Grafana 监控面板

**目标**：搭建 Prometheus + Grafana 监控体系。

**步骤**：
1. 启动 Prometheus，配置抓取 `/actuator/prometheus` 端点
2. 启动 Grafana，导入 JVM Micrometer Dashboard（ID: 4701）
3. 自定义指标面板：
   - `agent_task_duration_seconds_sum / agent_task_duration_seconds_count` → 平均任务耗时
   - `llm_cache_hit_rate` → 缓存命中率
   - `thread_pool_queue_size` → 线程池积压

---

## 7. 扩展挑战

### 挑战 1：新增主从协同策略（MasterSlaveStrategy）

**需求**：一个主 Agent 决策，多个从 Agent 执行后汇总给主 Agent 做最终判断。

**提示**：
1. 实现 `CollaborationStrategy` 接口
2. 第一个子任务为主 Agent，后续为从 Agent
3. 主 Agent 的结果决定哪些从 Agent 执行
4. 所有从 Agent 完成后，主 Agent 再次被调用做汇总

### 挑战 2：LLM 提供商故障切换

**需求**：当主要 LLM 提供商不可用时，自动切换到备用提供商。

**提示**：
1. 修改 `LLMService.resolveProvider()`
2. 在 `completion()` 方法中捕获网络异常
3. 维护提供商优先级列表和健康状态
4. 实现熔断器模式：连续失败 N 次后标记不可用，冷却期后重试

### 挑战 3：分布式 Agent 部署

**需求**：将 Agent 部署到多个 JVM 节点，支持远程调用。

**提示**：
1. 引入 Redis 作为分布式 GlobalContext 存储
2. 使用 Redis Pub/Sub 替代 Guava EventBus 实现跨节点事件
3. Agent 注册信息存入 Redis，支持多节点发现
4. DispatchHub 增加远程 Agent 调用能力（HTTP/gRPC）

### 挑战 4：Prompt A/B 测试框架

**需求**：同一 Agent 的不同 Prompt 版本随机分配，对比成功率。

**提示**：
1. 在 `PromptResolver.resolve()` 中增加分流逻辑（按 hash(userId) % 2）
2. 在 `AgentTaskItem` 中记录使用的 prompt_version
3. 定时统计各版本成功率的显著性差异
4. 实现自动切换到优胜版本

### 挑战 5：动态工作流 DAG 可视化

**需求**：将任务拆解结果可视化为 DAG 图，展示执行进度。

**提示**：
1. 在任务详情接口中增加 DAG 结构数据（节点 + 边）
2. 前端使用 dagre/d3.js 渲染 DAG 图
3. 实时更新节点状态（PENDING → RUNNING → SUCCESS/FAILED）
4. 支持通过 WebSocket 推送状态变更

---

## 附录

### A. 项目文件索引

```
src/main/java/com/agentoffice/
├── AgentOfficeApplication.java          # Spring Boot 入口
├── agent/
│   ├── BizAgent.java                    # Agent 统一接口 (核心)
│   ├── AgentConfig.java                 # Agent 配置模型
│   ├── event/AgentEvent.java            # 7 种事件类型
│   ├── impl/                            # 5 个内置 Agent
│   │   ├── CodeReviewAgent.java         # 代码审查
│   │   ├── TicketAgent.java             # 工单处理
│   │   ├── DocumentAgent.java           # 文档处理
│   │   ├── DataReportAgent.java         # 数据复盘
│   │   └── RagQaAgent.java              # RAG 答疑
│   └── registry/AgentRegistry.java      # Agent 注册中心
├── dispatch/
│   ├── DispatchHub.java                 # 调度中枢 (核心)
│   ├── TaskDecomposer.java              # 混合任务拆解
│   ├── GlobalContext.java               # 全局上下文
│   ├── event/                           # 事件监听器
│   └── strategy/                        # 5 种协同策略
│       ├── CollaborationStrategy.java   # 策略接口
│       ├── ParallelStrategy.java        # 并行
│       ├── SerialStrategy.java          # 串行
│       ├── RelayStrategy.java           # 接力
│       ├── ConditionalStrategy.java     # 条件分支
│       └── LoopStrategy.java            # 循环迭代
├── llm/
│   ├── LLMProvider.java                 # LLM 统一接口
│   ├── LLMService.java                  # LLM 服务门面 (核心)
│   ├── LLMCache.java                    # LRU 结果缓存
│   ├── OpenAIProvider.java              # OpenAI 适配器
│   └── AnthropicProvider.java           # Anthropic 适配器
├── security/
│   ├── JwtTokenProvider.java            # JWT 签发与校验
│   ├── JwtAuthenticationFilter.java     # 认证过滤器
│   ├── SecurityConfig.java              # Spring Security 配置
│   └── TenantContext.java               # 租户上下文 (ThreadLocal)
├── config/
│   ├── MybatisPlusConfig.java           # 多租户 SQL 拦截器
│   ├── ThreadPoolConfig.java            # Agent 线程池
│   ├── EventBusConfig.java              # 异步事件总线
│   └── JasyptConfig.java                # 加密配置
├── controller/                          # REST API
├── service/                             # 业务服务
├── entity/                              # 13 个实体类
├── mapper/                              # 13 个 Mapper 接口
├── prompt/PromptResolver.java           # Prompt 变量解析
├── monitor/AlertService.java            # 告警服务
├── schedule/                            # 定时任务
└── util/                                # 工具类
```

### B. 数据库表速查

| 表名 | 用途 | 租户隔离 |
|------|------|---------|
| `sys_user` | 系统用户 | 是 |
| `sys_role` | 角色定义 | 否 |
| `sys_permission` | 权限定义 | 否 |
| `sys_role_permission` | 角色-权限关联 | 否 |
| `agent_info` | Agent 注册信息 | 否 |
| `agent_task` | 协同任务主表 | 是 |
| `agent_task_item` | 子任务执行明细 | 是 |
| `agent_prompt_template` | Prompt 模板 | 否 |
| `kb_document` | 知识库文档 | 是 |
| `task_decompose_template` | 任务拆解模板 | 否 |
| `llm_token_daily_stat` | Token 日统计 | 否 |
| `approval_record` | 审批记录 | 否 |
| `agent_stat_report` | Agent 统计报表 | 是 |

### C. 配置项速查

| 配置路径 | 默认值 | 说明 |
|---------|--------|------|
| `jwt.secret` | - | JWT 签名密钥（Base64） |
| `jwt.expiration` | 86400000 | AccessToken 有效期（24h） |
| `llm.default-provider` | deepseek | 默认 LLM 提供商 |
| `llm.cache.enabled` | true | 是否启用 LLM 缓存 |
| `llm.cache.ttl-seconds` | 3600 | 缓存 TTL（秒） |
| `llm.cache.max-size` | 10000 | 最大缓存条目 |
| `agent.thread-pool.core-size` | 10 | Agent 线程池核心数 |
| `agent.thread-pool.max-size` | 50 | Agent 线程池最大数 |
| `agent.thread-pool.queue-capacity` | 200 | 线程池队列容量 |
| `task.default-timeout-seconds` | 300 | 子任务默认超时（秒） |
| `task.max-retry` | 3 | 最大重试次数 |
| `alert.webhook-url` | "" | 告警 Webhook 地址 |
| `alert.token-budget-warn-percent` | 80 | Token 预算预警百分比 |
