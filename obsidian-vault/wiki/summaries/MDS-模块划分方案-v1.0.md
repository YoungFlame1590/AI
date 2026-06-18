# MDS 模块划分方案 (Module Division Scheme)

## 1. 模块清单与职责定义

### 1.1 订单接单模块 (ORD)
- **核心职责**：处理客户订单创建、文件上传与合规校验（100MB/80页）、小瑕疵非阻塞备注、订单状态机初始化。（依据 SRS REQ-ORD-001~004）
- **不属于本模块**：价格计算（属QUO）、生产排产（属PRO）。
- **接口摘要**：`OrderSubmissionService.submit()`, `FileValidationService.validate()`
- **依赖模块**：AUD（审计留痕）, INFRA（文件存储OSS）。

### 1.2 报价审批模块 (QUO)
- **核心职责**：阶梯价自动计算、折扣互斥决策矩阵校验、分级审批路由（店长/总部）、附加服务加价确认。（依据 SRS REQ-QUO-001~004）
- **不属于本模块**：订单创建（属ORD）、支付扣款（属FIN）。
- **接口摘要**：`QuotationCalcService.calculate()`, `DiscountApprovalService.approve()`
- **依赖模块**：ORD（读取订单参数）, AUD（审批留痕）。

### 1.3 排产调度模块 (PRO)
- **核心职责**：信用额度校验与拦截、全款到账解锁、机台排产指令下发、大单保护锁、审批冻结期SLA暂停、异常兜底2步干预。（依据 SRS REQ-PRO-001~003）
- **不属于本模块**：跨店路由决策（属DLV）、财务核销（属FIN）。
- **接口摘要**：`ProductionDispatchService.dispatch()`, `CreditLimitService.check()`
- **依赖模块**：QUO（读取锁定价格）, DLV（触发跨店路由）, AUD（应急开关留痕）。

### 1.4 跨店配送模块 (DLV)
- **核心职责**：SLA优先路由引擎执行、库存/负荷/资质权重计算、配送任务下发、超时自动放行、财务核销状态覆盖。（依据 SRS REQ-DLV-001~002）
- **不属于本模块**：机台生产执行（属PRO）、发票开具（属FIN）。
- **接口摘要**：`RoutingEngineService.route()`, `DeliveryTaskService.assign()`
- **依赖模块**：PRO（接收排产触发）, FIN（同步核销状态）。

### 1.5 财务发票模块 (FIN)
- **核心职责**：电子发票触发（预开/交付后开）、红冲逆向控制、外协占比拦截、日结对账报表生成、尾差自动平账。（依据 SRS REQ-FIN-001~003）
- **不属于本模块**：订单状态流转（属ORD）、路由权重配置（属AUD）。
- **接口摘要**：`InvoiceService.issue()`, `ReconciliationService.dailyClose()`
- **依赖模块**：ORD（读取订单流水）, DLV（读取外协成本）, INFRA（支付/税务网关）。

### 1.6 权限审计模块 (AUD)
- **核心职责**：全量操作轨迹快照留存、高危越权操作前端强阻断、路由/价格参数配置管理、企微告警推送。（依据 SRS REQ-AUD-001~002）
- **不属于本模块**：具体业务逻辑执行（如计算报价、排产）。
- **接口摘要**：`AuditLogService.record()`, `PermissionGuard.check()`, `ConfigService.update()`
- **依赖模块**：无业务依赖，被所有模块依赖。

---

## 2. 模块自检报告

| 自检维度 | 检查结果 | 依据说明 |
|---|---|---|
| **完备性** | 通过 | 6大模块完全覆盖SRS 3.1节所有REQ编号（ORD-001~004, QUO-001~004, PRO-001~003, DLV-001~002, FIN-001~003, AUD-001~002），无功能遗漏。 |
| **正确性** | 通过 | 模块职责边界清晰，无重叠。例如：QUO仅负责“计算与审批”，不触碰“支付扣款”；PRO负责“排产”，不触碰“跨店路由决策”。 |
| **一致性** | 通过 | 模块命名与SRS 3.1节、UML用例图、数据字典严格保持一致。如统一使用“配送外协人员”相关逻辑归入DLV模块。 |
| **有效性** | 通过 | 接口摘要均基于SRS验收标准推导，具备可落地性。如 `RoutingEngineService.route()` 对应 REQ-DLV-001 的路由决策执行。 |
