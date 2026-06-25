# PrintShop v2 Print MIS

`generated-code/printshop-v2/` 是“图文快印门店连锁管理系统”的需求变更管理版本。它从 v1 准生产版 Print MIS 增量演进，新增 `CR-001 订单变更冻结`：处理中订单关键字段变更必须创建变更请求，冻结生产/SLA，等待店长审批。

## 技术栈

- Java 17
- Spring Boot 3
- Spring Security Basic Auth
- Spring Data JPA
- Flyway
- MySQL 8
- 原生 HTML/CSS/JavaScript 静态前端，使用 ES module 分层

## 代码分层

后端按业务能力拆包，API 路径保持不变：

```text
src/main/java/com/printshop/
  common/          统一响应、异常处理
  infra/           追踪、统计和外部系统防腐层入口
  mis/domain/      JPA 实体
  mis/repository/  Spring Data JPA 仓储
  mis/identity/    登录、当前用户、用户与门店查询
  mis/order/       订单、订单文件、访问策略、固定选项与自动计价
  mis/maintenance/ 管理员清空业务数据
  mis/quotation/   报价、折扣、审批
  mis/job/         作业单
  mis/production/  生产排程、完工质检
  mis/inventory/   库存、调整、低库存
  mis/delivery/    配送/外协与签收
  mis/finance/     发票、收款、退款
  mis/audit/       审计日志
  mis/reporting/   报表
  mis/dashboard/   当前用户工作台
```

前端入口为 `src/main/resources/static/index.html`，加载 `js/main.js`。前端模块：

```text
static/js/config.js   模块配置、角色菜单、订单选项、价目表
static/js/state.js    登录用户、当前模块、选中记录、编辑状态
static/js/api.js      fetch、鉴权 header、错误处理
static/js/orders.js   订单字段权限、固定下拉、金额预估
static/js/render.js   导航、表格、详情、指标、时间线渲染
static/js/main.js     事件绑定与启动入口
```

## 启动

先启动数据库：

```powershell
docker compose up -d mysql
```

MySQL 会映射到宿主机 `127.0.0.1:13307`，避免撞上本机已有的 `3306` MySQL，也避免和 v1 的 `printshop_v1` Flyway 历史冲突。

再启动系统：

```powershell
mvn spring-boot:run
```

如果 Docker 镜像下载失败，或只想快速本地演示，可使用 H2 demo profile：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

页面入口：

```text
http://127.0.0.1:8080/
```

Swagger：

```text
http://127.0.0.1:8080/swagger-ui.html
```

## 演示账号

系统默认只初始化基础门店和七类账号，不再初始化订单、报价、库存、配送、发票、付款或审计样例数据。登录后可以从客户下单开始自己走完整流程。

系统会初始化默认库存，包括 A4 纸、铜版纸、彩色墨粉/墨水和装订耗材。生产任务完工质检通过后，会按订单页数、份数和工艺自动扣减库存。

如果演示过程中产生了业务数据，使用 `admin/demo123` 登录后点击顶栏“清空业务数据”，会删除订单、报价、作业单、生产、配送、财务和审计数据，并保留基础门店、七类账号与默认库存。

| 账号 | 密码 | 角色 |
|---|---|---|
| `customer` | `demo123` | 客户 |
| `clerk` | `demo123` | 门店店员 |
| `manager` | `demo123` | 门店店长 |
| `ops` | `demo123` | 总部运营管理员 |
| `finance` | `demo123` | 财务人员 |
| `courier` | `demo123` | 配送/外协人员 |
| `admin` | `demo123` | 系统管理员 |

## 模块

- 身份与权限：登录、当前用户、七类演示账号。
- 客户门户：下单、文件上传、进度查询、发票申请。
- 门店接单：订单审核、文件检查、报价、异常备注。
- 报价审批：价格明细、折扣审批、报价确认。
- 作业单与生产：作业单、生产排程、完工和质检。
- 库存与物料：纸张/耗材库存、低库存预警、库存调整。
- 配送/外协：跨店/外协配送任务、签收、异常记录。
- 财务：收款、退款、开票、日结摘要。
- 审计与报表：操作日志、订单漏斗、生产负载、财务摘要、`/stats`。

## 订单权限与计价

- 客户只查看和维护自己的订单；门店店员/店长只查看本门店订单；总部运营、财务和管理员按职责查看全局订单；配送/外协只查看分配给自己或待接单的配送相关订单。
- 订单产品类型、颜色/工艺、交付方式和优先级均为固定选项。页面使用下拉框，后端会再次校验。
- 订单金额由后端按固定价目表计算，前端只显示预估金额；客户端传入的 `totalAmount` 不作为可信金额。
- 新建订单时客户默认为当前登录用户，交付时间默认填服务端创建时间。
- 客户可将早期订单提交审核，但不能越权流转到生产、交付等内部状态。

## CR-001 订单变更冻结

- 订单进入审核、报价、作业单、生产或配送后，产品类型、颜色/工艺、页数、份数、交付方式和优先级不得通过 `PUT /api/orders/{id}` 直接覆盖。
- 客户、店员、店长或管理员需要点击订单详情中的“申请变更”，系统会创建订单变更请求并冻结生产/SLA。
- 待审批变更存在时，排产、完工和配送创建会被后端拒绝。
- 店长或管理员在“订单变更”模块审批通过后，订单字段更新、金额重新计算并恢复流程；驳回后订单保持原字段并恢复流程。
- 创建、审批和驳回均记录审计日志。

## 订单快捷流转

工作台默认显示“我的待办任务”。点击任务会进入订单聚合详情，同一屏查看订单、报价、作业单、生产、配送、财务、变更、文件和时间线，并通过统一动作入口继续推进。模块导航仍保留为管理视图，适合批量查看和维护。

新增主流程接口：

- `GET /api/workbench/tasks`：按当前角色实时派生待办任务和指标。
- `GET /api/orders/{orderId}/aggregate`：读取订单聚合详情。
- `POST /api/orders/{orderId}/workflow/actions/{action}`：统一执行提交审核、报价、作业单、排产、完工、配送、签收、收款、开票、退款和申请变更。

订单详情提供角色化快捷动作，减少跨模块复制 ID：

- 客户：提交审核、申请变更、在已收款后申请发票或退款。
- 门店店员/店长/管理员：生成报价、生成作业单。
- 店长/总部运营/管理员：创建排产任务。
- 总部运营/配送外协/管理员：生成配送任务。
- 配送/外协人员：接受待接配送订单、签收配送任务；不显示上传/查看文件，也不能生成配送任务。
- 财务/管理员：登记收款、生成发票。
- 客户/财务/管理员：生成退款记录。
- 管理员：一键跑完整链路，用于课堂快速验收。

快捷动作仍会走后端权限校验、审计记录和 `/stats` 统计；不符合角色职责的操作会返回 403。

完整操作顺序见 [docs/操作流程.md](docs/操作流程.md)。系统会阻止越级操作，例如未提交审核不能报价、未完成生产质检不能生成配送、未收款不能开票或退款。

## 常用命令

```powershell
mvn test
mvn package
Get-ChildItem src\main\resources\static\js -Filter *.js | ForEach-Object { node --check $_.FullName }
node scripts\verify-design-drift.js --check
node scripts\verify-change-regression.js --check
```

生成或更新 RCR/质量校验/ADR-002~004：

```powershell
node scripts\verify-design-drift.js --write
node scripts\verify-change-regression.js --write
```

## 关键接口

- `/api/auth`
- `/api/me`
- `/api/stores`
- `/api/users`
- `/api/orders`
- `/api/order-change-requests`
- `/api/quotations`
- `/api/job-tickets`
- `/api/production-tasks`
- `/api/inventory-items`
- `/api/delivery-tasks`
- `/api/invoices`
- `/api/payments`
- `/api/audit-logs`
- `/api/reports`
- `/api/admin/business-data`
- `/stats`

## v1 边界

- 文件上传保存到本地 `uploads/`，数据库保存文件元数据。
- 支付、税务、短信/企微、设备/MQTT 使用可替换适配器思路，v1 不接真实第三方。
- Basic Auth 和明文演示密码仅用于课程本地演示，不用于生产。
