# 多 Agent 协同办公系统 - 项目设计文档

## 1. 项目概述

### 1.1 项目背景

现代企业日常办公、业务处理、研发运维、客服运营场景中，存在大量碎片化、重复性、流程化的办公任务，包含工单处理、代码审查、文档整理、数据统计、消息通知、流程审批等多类工作。传统办公模式依赖人工单点处理各类事务，系统工具割裂、业务互不打通、任务串行执行，存在大量行业痛点。

传统单智能体模式能力单一，仅能完成单一垂直场景任务，无法适配企业复杂、多维度、联动性强的办公需求；各类独立AI Agent无法互通数据、共享状态、协同分工，造成能力冗余、资源浪费、业务断层，无法形成完整办公闭环。同时人工办公存在效率低、响应慢、易遗漏、标准化差、复盘困难、人力成本高等问题。

基于以上痛点，本项目实现**Java原生多Agent协同办公系统**，摒弃重型AI框架与复杂中间件依赖，通过自研Agent调度中枢，实现多智能体分工协作、状态共享、任务分发、自动协同，整合工单处理、代码审查、文档AI、数据统计、智能答疑等多场景能力，构建一体化、自动化、智能化的企业办公协同中台。

### 1.2 项目定位

面向企业全场景的**多智能体协同办公AI中台实战项目**，整合多垂直领域Agent能力，实现多AI智能体分工、协同、调度、联动办公，替代碎片化人工操作，适配企业日常办公、研发质检、运维客服、数据复盘全场景。

区别于传统单Agent项目，本项目核心是 **多Agent分工自治 + 中枢智能调度 + 跨场景协同闭环**：

- 拆解复杂办公任务，分配对应专业Agent独立自治处理，各司其职
- 中枢调度器统一管控所有Agent，实现任务分发、优先级管控、状态同步、冲突消解
- 多Agent数据互通、能力互补，完成单一Agent无法实现的复杂复合型办公任务

### 1.3 核心目标

- 搭建标准化多Agent协同架构，支持多类业务Agent快速接入、插拔式扩展
- 实现任务智能拆解、自动分发、Agent分工执行，完成复杂复合型办公任务
- 打通代码审查、工单处理、文档处理、智能答疑、数据复盘多场景能力
- 实现多Agent状态统一管控、任务监控、异常兜底、日志全溯源
- 构建企业级智能办公闭环，大幅降低重复办公人力成本，提升团队办公效率
- 支持Agent能力扩展、业务场景新增、规则自定义配置，适配企业持续迭代需求

## 2. 需求分析

### 2.1 功能需求

#### 2.1.1 多Agent统一管理能力

- 支持多类业务Agent注册、启用、禁用、配置管理，实现插拔式接入
- 统一管理各Agent状态、能力标签、任务负载、运行日志
- 支持Agent权重配置、优先级配置、专属业务场景绑定

#### 2.1.2 智能任务调度协同能力

- 复杂任务自动拆解为多个子任务，根据Agent能力标签智能分发对应Agent
- 支持串行、并行、条件分支、循环迭代、依赖式多Agent协同执行模式
- 自动规避任务冲突、重复执行、资源抢占问题
- 支持任务超时监控、失败重试、异常熔断、兜底执行

#### 2.1.3 多垂直Agent业务能力（内置核心智能体）

- **工单处理Agent**：实现工单智能分类、分级、分派、自动答疑、办结、复盘统计
- **代码审查Agent**：实现代码规范校验、语义BUG检测、性能安全审查、代码优化重构
- **文档处理Agent**：支持文档解析、内容总结、要点提取、格式规整、智能改写
- **数据统计Agent**：自动汇总多场景数据、生成报表、趋势分析、问题复盘报告
- **智能答疑Agent**：基于RAG知识库，解答办公、研发、业务常见问题

#### 2.1.4 状态共享与数据协同能力

- 多Agent共享全局上下文、任务状态、业务数据，实现跨Agent联动
- 支持Agent之间结果互传、数据复用，避免重复计算、重复调用大模型
- 统一数据格式、统一输出规范，实现多Agent结果聚合汇总

#### 2.1.5 任务监控与复盘能力

- 全流程记录多Agent任务执行链路、执行耗时、执行结果、异常信息
- 统计各Agent负载、任务成功率、报错率、执行效率
- 自动生成多Agent协同办公复盘报告，优化任务分发策略

#### 2.1.6 多租户与权限管控能力

- 支持企业多部门、多团队独立使用，数据完全隔离、互不可见
- 实现基于RBAC的用户角色权限模型，普通用户、部门主管、管理员分级管控
- 任务操作、Agent管理、报告查看均受权限约束

### 2.2 非功能需求

- **稳定性**：多Agent高并发协同不冲突、任务不丢失、状态不混乱，支持大规模批量任务执行
- **可用性**：单一Agent故障不影响整体系统运行，具备任务转移、降级兜底策略
- **可扩展性**：遵循开闭原则，新增业务Agent无需改动核心调度代码，快速接入集群
- **安全性**：任务数据、代码数据、办公数据本地化存储，权限分级管控，多租户隔离，API Key加密存储，日志全程溯源
- **高性能**：多任务线程池隔离、Agent任务异步并行执行、LLM调用缓存复用、减少资源损耗

## 3. 整体架构设计

### 3.1 架构思想

采用**Java分层工程化 + 中枢调度 + 多智能体集群协同架构**，彻底区别于单Agent单点模式。系统以「中枢统一调度、各Agent自治执行、跨Agent协同联动、全局状态管控」为核心思想，所有Agent无独立入口，统一由调度中枢分发任务、管控生命周期，实现复杂办公任务的拆解、分发、执行、聚合、复盘全自动化闭环。

### 3.2 六层整体架构

#### 1）接入层（Controller）

统一接收前端各类办公请求、任务提交、Agent管理、报告查询指令，完成参数校验、权限校验、租户上下文注入、请求封装，统一提交至协同调度中枢，返回标准化聚合结果。

#### 2）安全与认证层

基于 Spring Security + JWT 实现无状态认证，RBAC 权限模型控制接口级、数据级访问。租户上下文从 Token 中提取并注入请求线程，所有数据操作自动过滤租户边界。大模型 API Key 加密存储，调用频率令牌桶限流。

#### 3）多Agent调度中枢层（系统核心）

整个系统的大脑与调度核心，负责全局管控：任务接收、复杂任务拆解、Agent能力匹配、智能分发、执行顺序管控、状态同步、冲突处理、结果聚合、异常兜底，统筹所有智能体的工作流程。

#### 4）智能体集群层（业务执行层）

所有垂直业务Agent集群，各司其职、独立自治、可插拔扩展，接收中枢下发的子任务并独立执行：

- 工单处理 Agent
- 代码审查 Agent
- 文档处理 Agent
- 数据统计复盘 Agent
- 智能答疑 RAG Agent

#### 5）通用工具能力层

为所有Agent和调度中枢提供公共可复用工具，统一能力标准：

- LLM大模型统一调用工具（多提供商抽象、流式输出、结果缓存、Token统计）
- 任务分片、异步执行工具
- 文本解析、数据格式化工具
- 相似度匹配、去重、对比工具
- 报告生成、数据统计工具

#### 6）基础服务层

包含数据库持久化、全局线程池、日志记录、异常捕获、任务重试、定时调度、状态缓存、权限管控、EventBus事件通信、Micrometer指标采集、Prometheus端点暴露等底层支撑能力。

### 3.3 多Agent协同核心业务流程

1. **任务接入**：用户提交复合型办公任务（如研发上线质检、运维工单复盘、项目文档汇总质检），携带租户令牌
2. **权限校验**：校验用户对该类型任务的提交权限，注入租户上下文
3. **任务智能拆解**：调度中枢分析任务意图，混合规则模板与LLM实时拆解，拆解为多个独立子任务，匹配对应专业Agent
4. **协同策略判定**：中枢根据任务依赖关系与规则配置，判定并行/串行/条件分支/循环迭代执行策略，规避任务冲突
5. **Agent分布式执行**：多Agent同时或按序执行各自子任务，独立完成自治工作，通过EventBus发布状态变更事件
6. **状态同步与数据互通**：各Agent执行结果全局同步，可互相复用数据、辅助对方任务完成
7. **人工审批节点**（可选）：敏感操作进入 WAITING_APPROVAL 状态，等待人工确认后继续
8. **结果聚合汇总**：调度中枢收集所有Agent执行结果，统一格式化、去重、整合
9. **报告生成与持久化**：生成完整协同办公结果与复盘报告，保存全流程记录（含 traceId 全链路可溯）

## 4. 安全与权限设计

### 4.1 认证体系

采用 **JWT（JSON Web Token）+ Spring Security** 无状态认证方案：

- 用户登录后服务端签发 JWT，内含 userId、tenantId、roleCode，设置合理过期时间
- 每次请求经由 SecurityFilterChain 校验 Token 有效性、解析租户上下文并注入请求线程
- 支持 Token 刷新机制，避免频繁重新登录
- 敏感操作（Agent 注册/删除、系统配置变更）要求二次认证

### 4.2 权限模型（RBAC）

用户 → 角色 → 权限 的三级授权体系：

| 角色 | 权限范围 |
|------|---------|
| 系统管理员 (ADMIN) | Agent 注册/启用/禁用/删除、全局配置、所有数据查看 |
| 部门主管 (MANAGER) | 本租户内任务提交/查看/审批、本租户报告查看 |
| 普通员工 (USER) | 本人提交任务的查看/取消、公共 Agent 能力列表查看 |

权限控制粒度：
- **接口级**：通过 `@PreAuthorize` 注解控制 Controller 方法访问权限
- **数据级**：所有查询自动追加 `tenant_id = ?` 条件，用户只能看到本租户数据

### 4.3 多租户数据隔离

- 所有业务数据表均包含 `tenant_id` 字段
- 请求进入时从 JWT 提取 tenantId，通过 ThreadLocal 传递
- MyBatis-Plus 自定义拦截器自动为 SQL 追加租户过滤条件（INSERT 自动填充、SELECT/UPDATE/DELETE 自动追加 WHERE tenant_id = ?）
- 超级管理员支持跨租户查看（平台运维场景）

### 4.4 大模型调用安全

- **API Key 管理**：各 LLM 提供商的 API Key 使用 AES-256 加密存储于配置表，运行时解密后注入，不在日志中打印
- **调用频率限制**：基于令牌桶算法实现，每个 Agent 独立限流，防止单个 Agent 过度调用
- **Token 消耗预算**：按天/按 Agent 维度设定 Token 消耗上限，达 80% 预警，达 100% 熔断
- **内容安全**：用户提交的代码/文档/工单内容不做持久化到外部 LLM 服务器（仅使用 API，不参与模型训练），敏感数据字段脱敏后发送

### 4.5 Prompt Injection 防护

- 用户输入内容在拼入 Prompt 前做转义处理，对 `###`、`---`、```` 等 Markdown 分隔符做特殊处理
- 维护敏感指令黑名单（如 "ignore previous instructions"、"system: "、"<|im_start|>" 等），匹配命中则拒绝执行
- 所有用户输入使用结构化 JSON 参数传递，而非直接字符串拼接，降低注入风险
- LLM 输出在返回前做格式校验，防止输出中包含可执行指令或脚本注入

### 4.6 数据脱敏

- 日志输出自动识别手机号、身份证号、邮箱、IP 地址、银行卡号，替换为脱敏格式（如 `138****1234`）
- 通过 Logback 自定义 Converter 实现，全局生效
- 数据库查询结果序列化时，敏感字段使用 `@JsonSerialize` 自定义序列化器脱敏

## 5. 核心模块详细设计

### 5.1 多Agent调度中枢模块（核心）

系统最核心模块，全权负责多智能体的生命周期管理与协同调度，是区别于单Agent项目的核心亮点：

- **Agent注册管理**：统一注册、发现、注销各业务Agent，维护Agent能力注册表

#### 5.1.1 任务拆解引擎

复杂任务拆解是整个系统的入口难关，采用**规则模板 + LLM 实时拆解**的混合策略：

**第一层：规则模板匹配（优先级高）**
- 预定义常见复合办公任务的拆解模板，存储于 `task_decompose_template` 配置表
- 示例："研发上线质检" → 子任务列表 [代码规范审查, 代码安全扫描, 文档完整性检查, 变更风险评估]
- 通过关键词匹配或任务类型分类快速命中模板，响应时间 < 100ms，零 Token 消耗

**第二层：LLM 实时拆解（模板未匹配时降级）**
- 构造结构化 Prompt，要求 LLM 以 JSON Schema 约束的格式输出拆解结果
- Prompt 中包含当前可用 Agent 的能力标签，以保证拆解结果与现有 Agent 能力匹配
- 拆解输出要求：子任务名称、子任务内容、推荐 Agent 能力标签、依赖关系（可选）

```
System: 你是一个任务拆解引擎。将用户提交的复杂办公任务拆解为子任务。
约束：
1. 子任务数量 1-20 个
2. 每个子任务必须匹配以下至少一个能力标签：[code_review, ticket_process, doc_analysis, data_report, rag_qa]
3. 如有依赖关系请标明前序子任务序号
4. 输出严格遵循 JSON Schema
```

**拆解结果校验**（LLM 输出后，规则引擎二次校验）：
- 子任务数量校验：1 ≤ count ≤ 20
- 能力标签校验：每个子任务的 requiredCapability 必须存在于 Agent 能力注册表中
- 依赖关系校验：检测依赖图中无循环引用（DAG 无环判定）
- 校验失败则返回校正后的 LLM Prompt 重试一次

**拆解失败兜底**：
- 重试仍失败，将原始任务整体作为单个子任务，分配给通用能力标签最多的 Agent 执行
- 记录拆解失败日志，便于后续分析优化模板

#### 5.1.2 智能分发策略

- 根据Agent能力标签、负载状态、优先级，精准匹配最优执行智能体
- 同一个子任务如有多个候选 Agent，优先选择当前负载最低、历史成功率最高的 Agent
- 支持亲和性调度：同一主任务的子任务尽量分配到同一 Agent 实例，减少上下文传输开销

#### 5.1.3 协同模式管控

支持以下七种协同模式，覆盖简单到复杂的全部办公场景：

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| 并行执行 (PARALLEL) | 多个子任务同时分发、同时执行 | 无依赖的多项检查（如同时做代码审查+文档审查） |
| 串行依赖 (SERIAL) | 子任务按依赖顺序依次执行 | 先分析后报告、先审查后修复 |
| 多轮接力 (RELAY) | Agent A 输出作为 Agent B 输入，链式传递 | 文档解析 → 内容总结 → 格式润色 |
| 主从协作 (MASTER_SLAVE) | 一个主 Agent 决策，多个从 Agent 分工执行后汇总给主 Agent | 复杂项目评估，主管 Agent 分配各维度评估任务 |
| 条件分支 (CONDITION) | Agent A 结果经规则判断，选择走 Agent B 或 C | 代码评分 > 70 跳过重构，否则进入重构流程 |
| 循环迭代 (LOOP) | 设定退出条件，循环执行直到满足 | 审查→修改→再审查，直到评分达标或达到最大轮次 |
| 人工审批 (APPROVAL) | 流水线中插入审批节点，等待人工确认后继续 | 敏感工单办结、代码自动修复、报告发布 |

条件分支规则示例：
```json
{
  "conditionField": "codeReviewScore",
  "operator": ">",
  "threshold": 70,
  "trueBranch": "END",
  "falseBranch": "code_refactor_agent"
}
```

循环迭代退出条件：
- 最大迭代轮次（如最多 5 轮），防止死循环
- 目标质量阈值（如代码评分 ≥ 80）
- 连续两轮改进幅度 < 阈值（收益递减，停止迭代）

#### 5.1.4 全局状态管控

- 统一维护任务进度、Agent运行状态、上下文数据，防止状态错乱
- 任务状态机：CREATED → DECOMPOSING → DISPATCHING → EXECUTING → WAITING_APPROVAL → AGGREGATING → COMPLETED / FAILED / CANCELLED
- 每个子任务独立状态机：PENDING → RUNNING → SUCCESS / FAILED / SKIPPED
- 主任务状态由其子任务状态计算：全部 SUCCESS → COMPLETED，任一 FAILED 且不可重试 → FAILED

#### 5.1.5 异常容错调度

- 单Agent失败自动重试（默认最多 3 次，指数退避）
- 重试耗尽仍失败且存在同能力备选 Agent → 任务迁移至空闲 Agent
- 无备选 Agent 可用 → 全局降级策略（跳过该子任务并标记 SKIPPED，或标记主任务 FAILED）
- 超时熔断：子任务超过 timeout_seconds 未完成，强制中断并进入重试/迁移流程

### 5.2 各垂直业务Agent模块

所有业务Agent遵循统一接口规范 `BizAgent`，独立自治、可插拔扩展，各自负责垂直领域智能化处理：

```java
public interface BizAgent {
    /** Agent 唯一编码 */
    String getAgentCode();
    /** 结构化能力描述（JSON格式） */
    AgentCapability getCapability();
    /** 执行子任务，返回结构化结果 */
    AgentResult execute(TaskItem item, GlobalContext context);
    /** 健康检查 */
    boolean healthCheck();
}
```

内置五大核心 Agent：

- **工单处理Agent**：负责工单接入、AI解析、分类分级、自动分派、RAG答疑、工单办结、工单超时预警、工单数据统计
- **代码审查Agent**：负责代码预处理、规则校验、语义BUG检测、性能安全审查、代码重构优化、代码质量评分（生成结构化 JSON 评审报告）
- **文档处理Agent**：支持各类办公文档、技术文档、业务文档的智能解析、内容总结、要点提取、改写润色、格式规整
- **数据复盘Agent**：聚合多Agent历史数据，自动统计办公效率、问题分布、高频故障、短板业务，生成周期复盘报告
- **RAG答疑Agent**：加载企业知识库、业务手册、运维文档，为所有Agent提供知识问答支撑，辅助任务决策

### 5.3 多Agent数据协同模块

解决多智能体数据割裂、无法互通的核心问题，实现全局数据共享复用。

#### 5.3.1 Agent 通信协议（EventBus）

同一 JVM 内采用 **Guava EventBus** 实现事件驱动通信，各 Agent 与调度中枢通过发布/订阅模式解耦：

**核心事件类型：**

| 事件 | 发布者 | 订阅者 | 说明 |
|------|--------|--------|------|
| `TaskDecomposedEvent` | 调度中枢 | 监控、日志 | 任务拆解完成，含子任务列表 |
| `TaskDispatchedEvent` | 调度中枢 | 目标Agent、监控 | 子任务已分发至指定 Agent |
| `TaskCompletedEvent` | 执行Agent | 调度中枢 | 子任务执行完成，携带结果 |
| `TaskFailedEvent` | 执行Agent | 调度中枢、告警 | 子任务执行失败，携带异常信息 |
| `GlobalContextUpdatedEvent` | 调度中枢 | 所有Agent | 全局上下文变更，Agent 按需读取 |
| `ApprovalRequiredEvent` | 调度中枢 | 通知服务 | 需要人工审批 |
| `ApprovalResolvedEvent` | 调度中枢 | 阻塞的Agent | 审批完成，继续或终止执行 |

**通信规则：**
- Agent 之间不直接通信，所有事件经 EventBus 由调度中枢统一中转
- 事件为异步非阻塞（`@Subscribe` + 独立线程池），不阻塞 Agent 主流程
- 事件携带 traceId，便于全链路串联
- EventBus 异常不影响主流程（异常被 EventBus 捕获并记录日志，不抛出至发布者）

#### 5.3.2 全局上下文存储

- **数据结构**：`ConcurrentHashMap<String, Object>`，key 为 `taskId:fieldName` 格式，支持并发安全读写
- **访问控制**：`ReentrantReadWriteLock`，读多写少场景优化（各 Agent 频繁读取全局上下文，只有中枢写入）
- **生命周期**：与主任务绑定，主任务完成后异步写入数据库归档，内存中保留 24 小时后清除
- **内容范围**：原始任务内容、拆解后的子任务列表、各子任务执行结果、中间产物（如 Agent A 解析的文档结构供 Agent B 使用）

#### 5.3.3 数据协同规则

- 统一上下文存储，所有Agent可读写全局任务上下文
- 任务结果全局缓存，避免重复调用大模型、重复计算
- 统一数据输出规范，所有Agent返回结构化标准化数据 `AgentResult`，便于中枢聚合
- 支持跨Agent数据联动：工单Agent可调用答疑Agent知识库辅助回复，代码Agent结果可被复盘Agent统计分析

### 5.4 任务监控与容错模块

保障多Agent高并发协同的稳定性，解决多任务冲突、阻塞、异常问题：

- 自定义隔离线程池，不同类型Agent任务线程隔离，互不干扰
- 任务超时熔断机制，防止单任务卡死整体协同流程
- 失败任务自动重试（指数退避）、降级兜底、任务迁移机制
- 全链路日志记录，精准定位异常Agent、异常节点

#### 5.4.1 可观测性方案

**日志链路追踪**
- 任务提交时生成全局唯一 `traceId`，贯穿 主任务 → 子任务 → Agent执行 → LLM调用 全链路
- 日志格式：`[traceId:xxx] [agent:xxx] [task:xxx] message`
- 通过 SLF4J MDC 传递 traceId，线程池任务传递需手动包装（`MDCContextRunnable`）

**指标采集（Micrometer + Prometheus）**

| 指标类别 | 具体指标 | 说明 |
|---------|---------|------|
| 任务指标 | `agent_task_total` / `agent_task_success` / `agent_task_duration_seconds` | 各 Agent 任务量、成功率、耗时分布 |
| LLM 指标 | `llm_token_consumed_total` / `llm_call_duration_seconds` | 按 Agent 维度统计 Token 消耗与调用延迟 |
| 线程池指标 | `thread_pool_queue_size` / `thread_pool_active_threads` | 线程池队列积压与活跃线程数 |
| 缓存指标 | `llm_cache_hit_rate` | LLM 结果缓存命中率 |
| 业务指标 | `ticket_closed_total` / `code_review_score_avg` | 业务维度统计 |

指标通过 `/actuator/prometheus` 端点暴露，Prometheus 定时拉取，Grafana 可视化。

**告警规则**

| 告警条件 | 级别 | 通知方式 |
|---------|------|---------|
| 同一 Agent 连续失败 > 3 次 | P1 严重 | 钉钉/企微群通知 + @负责人 |
| Token 日消耗超预算 80% | P2 预警 | 钉钉/企微群通知 |
| Token 日消耗达 100%（熔断） | P1 严重 | 钉钉/企微群通知 + 自动熔断 |
| 任务队列积压 > 阈值（默认 50） | P2 预警 | 钉钉/企微群通知 |
| Agent 健康检查连续失败 > 5 分钟 | P1 严重 | 钉钉/企微群通知 + @负责人 |

### 5.5 Prompt 管理方案

每个 Agent 的行为由 Prompt 模板驱动，Prompt 的质量直接决定 Agent 输出质量。

#### 5.5.1 Prompt 模板化

Prompt 存储于数据库 `agent_prompt_template` 表，支持变量占位符 `{{变量名}}`：

```
示例（代码审查 Agent 的 Prompt 模板）：
你是一个代码审查专家。请审查以下代码：
- 关注点：{{review_focus}}
- 语言：{{language}}
- 代码：
{{code_content}}

输出 JSON 格式：
{
  "score": <0-100>,
  "issues": [{"severity": "error|warning|info", "line": <行号>, "description": "<问题描述>", "suggestion": "<修复建议>"}],
  "summary": "<总结>"
}
```

#### 5.5.2 版本管理

- 每次修改 Prompt 模板生成新版本号（v1, v2, v3...），旧版本保留不可删除
- Agent 执行时默认使用最新启用版本，支持手动回滚到指定历史版本
- Prompt 变更后需在测试任务集上验证效果，记录效果报告后再切换生产版本

#### 5.5.3 变量注入

运行时由 `PromptResolver` 解析变量：
- 固定变量：从任务参数中提取（`{{task.content}}`、`{{language}}`）
- 动态变量：从全局上下文中读取（`{{global.review_focus}}`）
- Agent 专属变量：从 Agent 配置中读取（`{{agent.config.model}}`）

#### 5.5.4 效果评估

- 记录每个 Prompt 版本对应的任务成功率（`agent_stat_report` 中关联 prompt_version）
- 支持 A/B 对比：同一类任务随机分配不同 Prompt 版本，对比成功率与输出质量评分
- 低质量 Prompt（成功率 < 阈值）自动告警，提示运营人员优化

### 5.6 LLM 调用层设计

统一封装所有对大模型的调用，屏蔽不同提供商的差异。

#### 5.6.1 统一接口

```java
public interface LLMProvider {
    /** 同步普通对话 */
    String completion(String systemPrompt, String userPrompt, String model, Map<String, Object> params);
    /** 流式对话（SSE），返回 Flux 供 WebFlux 推送 */
    Flux<String> completionStream(String systemPrompt, String userPrompt, String model, Map<String, Object> params);
    /** 文本向量化（RAG 场景） */
    List<Float> embed(String text, String model);
    /** 提供商标识 */
    String getProviderName();
}
```

每个提供商一个实现类：`OpenAIProvider`、`AnthropicProvider`、`QwenProvider`、`WenXinProvider` 等，通过配置 `llm.provider.active` 切换。

#### 5.6.2 调用缓存

- 基于 `MD5(systemPrompt + userPrompt + model)` 的 LRU 内存缓存
- 缓存 Key 包含 model 参数，不同模型结果不混用
- 缓存 TTL 可配置（默认 1 小时），通过 Micrometer 上报命中率
- 全局缓存开关：开发调试阶段可关闭，生产环境开启
- 缓存大小上限可配置（默认 10000 条），超出后 LRU 淘汰

#### 5.6.3 Token 统计与成本控制

- 每次 LLM 调用记录 promptTokens + completionTokens + model + agentCode
- 按天汇总每个 Agent 的 Token 消耗，存储于 `llm_token_daily_stat` 表
- 预算控制：Agent 维度配置 `dailyTokenBudget`，达 80% 预警，达 100% 熔断（该 Agent 当天禁止调用 LLM，返回兜底响应）
- 成本核算：按各模型官方定价自动折算为费用，生成 Token 消耗报表

#### 5.6.4 流式输出

- 返回类型 `Flux<String>`，通过 Spring WebFlux SSE 推送给前端
- 适用场景：代码审查报告实时展示、文档 AI 处理进度、RAG 答疑流式回复
- 非流式场景（如数据统计）直接调用 `completion()` 同步方法

### 5.7 报表与智能复盘模块

聚合所有Agent运行数据、任务执行数据、业务处理数据，实现办公全场景复盘：

- 各Agent负载统计、任务成功率、执行耗时分析
- 企业工单、代码质量、文档处理、答疑问题全维度数据汇总
- AI自动分析办公短板、高频问题、效率瓶颈
- 输出优化建议，辅助优化Agent调度策略与企业办公流程

## 6. 数据库设计

### 6.1 用户与权限表

#### 系统用户表（sys_user）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| username | varchar(50) | 用户名，唯一 |
| password | varchar(255) | BCrypt 加密密码 |
| real_name | varchar(50) | 真实姓名 |
| email | varchar(100) | 邮箱 |
| tenant_id | bigint | 所属租户ID |
| role_id | bigint | 角色ID |
| status | tinyint | 0禁用 1启用 |
| create_time | datetime | 创建时间 |

#### 角色表（sys_role）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| role_name | varchar(50) | 角色名称 |
| role_code | varchar(50) | 角色编码：ADMIN / MANAGER / USER |
| description | varchar(200) | 角色描述 |
| create_time | datetime | 创建时间 |

#### 权限表（sys_permission）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| permission_code | varchar(100) | 权限编码，如 agent:register / task:submit / report:view |
| permission_name | varchar(100) | 权限名称 |
| create_time | datetime | 创建时间 |

#### 角色权限关联表（sys_role_permission）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| role_id | bigint | 角色ID |
| permission_id | bigint | 权限ID |

### 6.2 智能体与协同任务表

#### 智能体注册表（agent_info）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键ID |
| agent_code | varchar(50) | 智能体唯一编码 |
| agent_name | varchar(100) | 智能体名称 |
| agent_capability | text | 能力标签（结构化JSON，见下方说明） |
| config_json | text | Agent 配置参数 JSON（model, temperature, max_tokens, dailyTokenBudget 等） |
| priority | int | 执行优先级（数值越小优先级越高） |
| max_concurrency | int | 最大并发执行数 |
| avg_cost_time | int | 历史平均执行耗时(ms) |
| status | tinyint | 0禁用 1启用 |
| create_time | datetime | 注册时间 |
| update_time | datetime | 更新时间 |

能力标签 `agent_capability` 字段（结构化 JSON 替代逗号分隔字符串）：

```json
{
  "capabilities": ["code_review", "bug_detection", "security_scan"],
  "inputFormats": ["text/plain", "application/json"],
  "outputFormats": ["application/json"],
  "supportedModels": ["gpt-4", "claude-4"],
  "preconditions": []
}
```

配置参数 `config_json` 字段：

```json
{
  "model": "gpt-4",
  "temperature": 0.3,
  "maxTokens": 4096,
  "dailyTokenBudget": 100000,
  "retryMax": 3,
  "retryBackoffMs": 1000,
  "timeoutSeconds": 300
}
```

#### 协同任务主表（agent_task）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| task_no | varchar(50) | 任务唯一编号 |
| task_name | varchar(200) | 任务名称 |
| task_content | text | 任务原始内容 |
| coop_mode | varchar(30) | 协同模式：PARALLEL / SERIAL / RELAY / MASTER_SLAVE / CONDITION / LOOP / APPROVAL |
| priority | int | 任务优先级 |
| timeout_seconds | int | 全局超时时间(秒) |
| task_status | tinyint | 0处理中 1已完成 2失败 3已取消 4等待审批 |
| result_summary | longtext | 任务聚合结果总结 |
| submit_user_id | bigint | 提交用户ID |
| tenant_id | bigint | 所属租户ID |
| trace_id | varchar(50) | 全链路追踪ID |
| create_time | datetime | 创建时间 |
| finish_time | datetime | 完成时间 |

#### 子任务执行明细表（agent_task_item）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| task_id | bigint | 关联主任务ID |
| agent_code | varchar(50) | 执行智能体编码 |
| item_content | text | 子任务内容 |
| item_result | longtext | 子任务执行结果（JSON格式） |
| status | tinyint | 0待执行 1执行中 2成功 3失败 4跳过 5等待审批 |
| error_msg | text | 异常信息 |
| retry_count | int | 已重试次数 |
| max_retry | int | 最大重试次数 |
| cost_time | int | 执行耗时(ms) |
| tenant_id | bigint | 所属租户ID |
| create_time | datetime | 创建时间 |
| finish_time | datetime | 完成时间 |

### 6.3 Prompt 与知识库表

#### Prompt 模板表（agent_prompt_template）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| template_code | varchar(100) | 模板编码，如 code_review_v3 |
| template_content | longtext | 模板内容（含 {{变量}} 占位符） |
| version | varchar(20) | 版本号：v1, v2, v3... |
| agent_code | varchar(50) | 绑定的 Agent 编码 |
| status | tinyint | 0草稿 1启用 2已废弃 |
| success_rate | decimal(5,2) | 该版本任务成功率(%) |
| create_time | datetime | 创建时间 |

#### 知识库文档表（kb_document）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| doc_name | varchar(200) | 文档名称 |
| doc_content | longtext | 文档原始内容 |
| doc_type | varchar(50) | 文档类型：manual / policy / faq / api_doc |
| vector_id | varchar(100) | 向量数据库中的文档ID（预留） |
| agent_code | varchar(50) | 关联的 Agent 编码（NULL 表示全局共享） |
| tenant_id | bigint | 所属租户ID |
| status | tinyint | 0禁用 1启用 |
| create_time | datetime | 创建时间 |

#### 任务拆解模板表（task_decompose_template）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| template_name | varchar(100) | 模板名称 |
| trigger_keywords | varchar(500) | 触发关键词（逗号分隔），如 "上线,质检,发布" |
| sub_tasks_json | longtext | 预定义子任务列表 JSON |
| coop_mode | varchar(30) | 默认协同模式 |
| priority | int | 模板优先级（数值越小越优先匹配） |
| status | tinyint | 0禁用 1启用 |

### 6.4 监控与审计表

#### LLM Token 日统计表（llm_token_daily_stat）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| stat_date | date | 统计日期 |
| agent_code | varchar(50) | Agent 编码 |
| model | varchar(50) | 模型名称 |
| prompt_tokens | bigint | 输入 Token 数 |
| completion_tokens | bigint | 输出 Token 数 |
| total_tokens | bigint | 总 Token 数 |
| estimated_cost | decimal(10,4) | 预估费用(美元) |
| call_count | int | 调用次数 |
| cache_hit_count | int | 缓存命中次数 |

#### 审批记录表（approval_record）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| task_item_id | bigint | 关联子任务ID |
| approver_id | bigint | 审批人用户ID |
| status | tinyint | 0待审批 1已通过 2已驳回 3超时自动处理 |
| comment | text | 审批意见 |
| agent_output_snapshot | longtext | 审批时的 Agent 输出快照 |
| create_time | datetime | 创建时间 |
| resolve_time | datetime | 审批完成时间 |

#### Agent 协同统计报表表（agent_stat_report）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| report_type | varchar(20) | 日/周/月报表：DAILY / WEEKLY / MONTHLY |
| agent_code | varchar(50) | 智能体编码 |
| total_task | int | 总任务数 |
| success_task | int | 成功任务数 |
| fail_task | int | 失败任务数 |
| avg_cost_time | int | 平均执行耗时(ms) |
| prompt_version | varchar(20) | 使用的 Prompt 版本号 |
| report_content | longtext | AI统计复盘内容 |
| tenant_id | bigint | 所属租户ID |
| create_time | datetime | 创建时间 |

## 7. 核心接口设计

所有接口采用 RESTful 风格，统一前缀 `/api/v1`，返回统一响应体：

```json
{
  "code": 0,
  "message": "success",
  "data": { }
}
```

### 7.1 认证与用户接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/auth/login | 用户登录，返回 JWT | 公开 |
| POST | /api/v1/auth/refresh | Token 刷新 | 公开（需有效 refreshToken） |
| GET | /api/v1/users/me | 获取当前用户信息 | 登录用户 |

### 7.2 任务协同接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/tasks | 提交协同任务 | USER |
| GET | /api/v1/tasks | 分页查询任务列表（支持按状态/类型/时间筛选） | USER（仅看自己的）+ MANAGER（看本租户全部） |
| GET | /api/v1/tasks/{taskId} | 查询任务详情（含子任务列表与执行链路） | 任务提交者或 MANAGER |
| DELETE | /api/v1/tasks/{taskId} | 取消任务（仅限处理中状态） | 任务提交者或 MANAGER |
| POST | /api/v1/tasks/{taskId}/retry | 重试失败任务 | 任务提交者或 MANAGER |
| POST | /api/v1/tasks/{taskItemId}/approve | 审批通过子任务 | MANAGER 或指定审批人 |
| POST | /api/v1/tasks/{taskItemId}/reject | 驳回子任务 | MANAGER 或指定审批人 |

分页查询参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 20，最大 100 |
| status | int | 否 | 任务状态筛选 |
| coopMode | string | 否 | 协同模式筛选 |
| startTime | string | 否 | 创建时间起始 |
| endTime | string | 否 | 创建时间截止 |

### 7.3 Agent 管理接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/v1/agents | 注册新 Agent | ADMIN |
| PUT | /api/v1/agents/{agentCode} | 更新 Agent 配置 | ADMIN |
| DELETE | /api/v1/agents/{agentCode} | 注销 Agent（软删除） | ADMIN |
| GET | /api/v1/agents | 获取 Agent 能力列表 | 登录用户 |
| GET | /api/v1/agents/{agentCode} | 获取 Agent 详情（含配置） | ADMIN |
| PATCH | /api/v1/agents/{agentCode}/status | 启用/禁用 Agent | ADMIN |
| GET | /api/v1/agents/metrics | 获取所有 Agent 运行指标 | ADMIN + MANAGER |

Agent 注册请求体示例：

```json
{
  "agentCode": "code_review_agent",
  "agentName": "代码审查Agent",
  "agentCapability": {
    "capabilities": ["code_review", "bug_detection", "security_scan"],
    "inputFormats": ["text/plain", "application/json"],
    "outputFormats": ["application/json"],
    "supportedModels": ["gpt-4", "claude-4"],
    "preconditions": []
  },
  "config": {
    "model": "gpt-4",
    "temperature": 0.3,
    "maxTokens": 4096,
    "dailyTokenBudget": 100000,
    "retryMax": 3,
    "timeoutSeconds": 300
  },
  "priority": 1,
  "maxConcurrency": 3
}
```

### 7.4 报表与监控接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/v1/reports | 获取协同复盘报告（支持日/周/月维度） | MANAGER |
| GET | /api/v1/reports/token-usage | Token 消耗统计报表 | ADMIN |
| GET | /api/v1/reports/agent-stats | Agent 维度统计报表 | MANAGER |

报表查询参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 否 | 报表类型：DAILY / WEEKLY / MONTHLY，默认 MONTHLY |
| agentCode | string | 否 | 指定 Agent 编码筛选，不传则查全部 |
| startDate | string | 否 | 统计起始日期 |
| endDate | string | 否 | 统计截止日期 |

### 7.5 Prompt 管理接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/v1/prompts | 查询 Prompt 模板列表 | ADMIN |
| GET | /api/v1/prompts/{templateId} | 查看模板详情与历史版本 | ADMIN |
| POST | /api/v1/prompts | 创建新 Prompt 模板版本 | ADMIN |
| PUT | /api/v1/prompts/{templateId}/activate | 启用指定版本 | ADMIN |
| POST | /api/v1/prompts/{templateId}/rollback | 回滚到指定历史版本 | ADMIN |

### 7.6 知识库管理接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/v1/knowledge-base | 分页查询知识库文档列表 | 登录用户 |
| POST | /api/v1/knowledge-base | 上传/新增知识库文档 | MANAGER |
| PUT | /api/v1/knowledge-base/{docId} | 更新文档内容 | MANAGER |
| DELETE | /api/v1/knowledge-base/{docId} | 删除文档 | MANAGER |

## 8. 技术栈选型

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 核心框架 | Spring Boot | 3.5.x | 应用框架 |
| 安全框架 | Spring Security | 6.x | 认证授权 |
| 认证方案 | jjwt (io.jsonwebtoken) | 0.12.x | JWT 签发与校验 |
| 数据库 | MySQL | 8.0 | 主数据库 |
| 持久层 | MyBatis-Plus | 3.5.x | ORM + 多租户拦截器 |
| 事件通信 | Guava EventBus | 33.x | Agent 间事件驱动通信 |
| 任务调度 | 自定义多线程隔离线程池 + Spring Schedule | - | 异步任务执行与定时统计 |
| 可观测性 | Micrometer + Prometheus | - | 指标采集与暴露 |
| 工具依赖 | Hutool | 5.8.x | 通用工具集 |
| JSON 处理 | FastJSON2 | 2.x | JSON 序列化/反序列化 |
| API 文档 | SpringDoc OpenAPI (Knife4j) | 2.x | API 文档自动生成 |
| 加密 | Jasypt | 3.x | API Key AES 加密存储 |
| AI 能力 | 原生 HTTP 大模型调用 + 结构化 Prompt 约束输出 | - | 多 LLM 提供商适配 |
| 流式响应 | Spring WebFlux (Reactor) | - | SSE 流式推送给前端 |
| 协同能力 | 自研多Agent调度引擎、任务拆解算法、状态同步机制 | - | 项目核心 |

## 9. 项目核心亮点（面试/简历重点）

- **自研多Agent协同调度架构**：不依赖任何AI框架（LangChain/AutoGen等），手写完整多智能体集群调度、任务拆解、协同执行、结果聚合核心逻辑。混合规则模板+LLM实时拆解，彻底掌握多Agent协作底层原理，区别于普通单Agent Demo

- **七种协同模式全覆盖**：自研并行、串行、接力、主从、条件分支、循环迭代、人工审批七种模式，可适配所有复杂办公任务场景。条件分支和循环迭代的加入使用户可构建复杂工作流，解决单Agent能力边界问题

- **多场景能力整合闭环**：整合代码审查、工单处理、文档AI、数据复盘、智能答疑多垂直场景，实现单一系统全覆盖企业办公智能场景，业务价值极高

- **插拔式Agent集群设计**：遵循开闭原则，新增业务Agent无需修改核心代码，实现 BizAgent 接口并注册即可接入协同体系。能力注册表采用结构化JSON描述，支持前置条件检查。扩展性极强，贴合企业中台设计思想

- **完整的安全与多租户体系**：JWT + Spring Security + RBAC 权限模型 + 多租户数据隔离 + API Key 加密存储 + Prompt Injection 防护 + 数据脱敏，达到企业级安全标准

- **高可用容错协同机制**：线程隔离、任务熔断、失败迁移、降级兜底、指数退避重试，保证多Agent高并发稳定运行，达到企业级工程化标准

- **LLM 调用层统一抽象**：多提供商适配（OpenAI/Anthropic/国产模型）、结果缓存复用、Token 消耗统计与预算控制、流式 SSE 输出，降低大模型使用成本

- **数据互通能力闭环**：EventBus 事件驱动实现 Agent 间解耦通信，全局上下文 ConcurrentHashMap + 读写锁实现高效安全共享，解决多Agent数据割裂、资源浪费的核心痛点

- **全链路可观测**：traceId 贯穿任务全生命周期，Micrometer + Prometheus + Grafana 采集任务/LLM/线程池/缓存多维指标，分级告警规则，日志脱敏，运维友好

- **Human-in-the-Loop 人工审批**：关键节点可插入人工审批，审批超时自动升级，审批结果驱动后续 Agent 执行分支，实现人机协同闭环

## 10. 项目扩展方向

- 引入 Redis 实现全局任务缓存、分布式锁，支持分布式多节点 Agent 部署
- 接入消息队列（RabbitMQ/Kafka），实现高并发任务削峰，支撑大规模批量协同任务
- 新增流程审批Agent、会议纪要Agent、数据爬虫Agent，持续丰富智能体集群
- 接入向量数据库（Milvus/Chroma），升级全局 RAG 知识库，所有 Agent 共享企业私有知识
- 对接钉钉、企业微信、OA系统，实现智能办公消息推送、告警通知、流程联动
- 实现 AI 动态调度策略，基于 Agent 历史负载与成功率自动优化任务分发权重
- 构建 Prompt 效果评估平台，自动 A/B 测试与版本推荐
- 支持多 LLM 负载均衡与自动故障切换（如 GPT-4 不可用时自动切换至 Claude）

## 11. 部署运行流程

1. 执行配套 SQL 脚本，创建所有数据表（用户/角色/权限/角色权限关联/Agent/任务/子任务/Prompt/知识库/拆解模板/审批/Token统计/报表等共 13 张表）
2. 初始化管理员账号、默认角色、基础权限数据
3. 初始化 5 个内置 Agent 的注册数据与默认 Prompt 模板
4. 初始化常见任务类型的拆解模板数据
5. 配置 application.yml：数据库连接、JWT 密钥、各 LLM 提供商 API Key（自动 AES 加密存储）、线程池参数、告警 Webhook URL
6. 启动项目，Spring 容器自动扫描并注册内置 Agent 实现类，初始化 EventBus 订阅关系
7. 登录获取 Token，提交复合型协同任务，验证任务拆解、多Agent 协同执行、结果聚合全流程
8. 查看 Prometheus 指标端点（/actuator/prometheus）、任务执行日志（按 traceId 搜索）、自动生成的复盘报告，完成全流程验证
