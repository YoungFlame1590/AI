# TLCD 三层约束文档 (Three-Layer Constraint Document)

## 1. 职责约束 (Responsibility Constraints)

| 编号 | 约束内容 | 适用范围 | 禁止事项 | 来源依据 |
|---|---|---|---|---|
| C-RESP-001 | 订单模块(ORD)仅负责接单、文件合规校验与状态机初始化，严禁处理任何价格计算与折扣逻辑。 | ORD模块 | 禁止在ORD模块中引入或计算价格字段，禁止ORD直接调用QUO模块的内部计算逻辑。 | SRS 3.1 ORD模块, MDS 1.1 |
| C-RESP-002 | 报价模块(QUO)必须实现互斥折扣决策矩阵，所有折扣阈值与路由规则必须抽象为配置项，严禁硬编码。 | QUO模块 | 禁止在代码中写死 `if (discount > 0.1)`，禁止绕过决策矩阵直接放行折扣。 | SRS REQ-QUO-004, ASD 3.3 |
| C-RESP-003 | 排产模块(PRO)负责信用额度校验、机台指令下发与异常兜底，严禁直接处理跨店路由决策。 | PRO模块 | 禁止PRO模块直接调用DLV模块的路由引擎，跨店调度必须通过事件或标准接口触发。 | MDS 1.3, DTS 2.2 |
| C-RESP-004 | 财务模块(FIN)涉及支付、退款、发票、对账的操作，必须在同一个本地数据库事务内完成。 | FIN模块 | 禁止使用分布式事务（如Seata/Saga），禁止将财务核销状态与支付资金状态耦合。 | SRS REQ-FIN-002, ASD 3.4, ADR-001 |
| C-RESP-005 | 审计模块(AUD)作为横切关注点，所有业务模块的写操作、配置变更必须经过审计切面。 | 全局业务模块 | 禁止业务代码绕过AUD模块直接写入审计日志，禁止人工关闭审计留痕开关。 | SRS REQ-AUD-001, ASD 3.2 |

## 2. 接口约束 (Interface Constraints)

| 编号 | 约束内容 | 适用范围 | 禁止事项 | 来源依据 |
|---|---|---|---|---|
| C-API-001 | 模块间通信必须通过 Application Service 接口或领域事件，严禁跨模块直接访问对方的 Repository。 | 所有模块间交互 | 禁止跨模块直接 Join 表，禁止注入其他模块的 DAO/Repository 接口。 | ASD 3.1, DTS 4 |
| C-API-002 | 外部系统接口（支付、税务、企微、MQTT）必须由 INFRA 模块通过防腐层(ACL)适配，业务模块不感知外部协议。 | INFRA及业务模块 | 禁止业务模块直接调用微信/支付宝/税务API，禁止在业务代码中处理TCP/MQTT协议。 | MDS 1.6, ASD 3.4 |
| C-API-003 | 核心实体状态流转必须通过统一的状态机引擎接口 `StateMachine.transit()` 驱动。 | ORD, PRO, DLV等状态机模块 | 严禁在业务代码中直接调用 `entity.setStatus()` 修改状态，严禁跳步流转。 | ASD 3.2, SRS 1.3 |
| C-API-004 | 所有对外暴露的 RESTful API 必须包含标准错误码体系与审计追踪头（如 X-Trace-Id）。 | 所有 Controller 层 | 禁止返回非标准格式的错误信息，禁止丢失链路追踪标识。 | SRS 3.2, ASD 3.4 |
| C-API-005 | 机台扫码接口必须由 INFRA 模块统一接入并转换为内部领域事件，业务模块仅消费事件。 | INFRA, PRO模块 | 禁止 PRO 模块直接监听 MQTT Topic，禁止在业务层处理硬件协议解析。 | SRS 3.3, MDS 1.6 |

## 3. 依赖约束 (Dependency Constraints)

| 编号 | 约束内容 | 适用范围 | 禁止事项 | 来源依据 |
|---|---|---|---|---|
| C-DEP-001 | 主业务流必须遵循 ORD -> QUO -> PRO -> DLV -> FIN 的单向依赖链路，严禁逆向控制。 | 核心业务模块 | 禁止 FIN 依赖 PRO，禁止 DLV 依赖 QUO，禁止逆向修改上游模块数据。 | DTS 1.1, SRS 1.3 |
| C-DEP-002 | 所有业务模块均允许依赖 AUD 和 INFRA，但 AUD 和 INFRA 严禁依赖任何业务模块。 | AUD, INFRA及业务模块 | 禁止 AUD 模块引入 ORD/QUO 等业务包，禁止 INFRA 依赖业务逻辑。 | DTS 1.1, ASD 3.1 |
| C-DEP-003 | 严禁跨级跳跃依赖，ORD 严禁直接依赖 PRO 或 DLV，必须经过 QUO 报价锁定。 | ORD, PRO, DLV | 禁止 ORD 模块直接触发排产或配送指令，必须等待 QUO 模块完成价格校验。 | DTS 1.2, MDS 1.1 |
| C-DEP-004 | 严禁同级横向双向依赖，QUO 与 PRO 之间严禁双向依赖，必须通过事件或单向接口解耦。 | QUO, PRO | 禁止 QUO 和 PRO 互相注入对方的 Service，避免循环依赖。 | DTS 1.2, ASD 3.1 |
| C-DEP-005 | FIN 模块允许只读依赖 ORD 和 DLV 以获取对账所需基础数据，但严禁写入或修改其数据。 | FIN, ORD, DLV | 禁止 FIN 模块调用 ORD/DLV 的写操作接口，禁止 FIN 直接修改订单或配送状态。 | DTS 1.1, MDS 1.5 |
