# PrintShop v1 Print MIS

`generated-code/printshop-v1/` 是“图文快印门店连锁管理系统”的 v1 准生产版业务工程。它不再是接口演示台，而是一个可登录、可持久化、可审计的 Print MIS 模块化单体。

## 技术栈

- Java 17
- Spring Boot 3
- Spring Security Basic Auth
- Spring Data JPA
- Flyway
- MySQL 8
- 原生 HTML/CSS/JavaScript 静态前端

## 启动

先启动数据库：

```powershell
docker compose up -d mysql
```

MySQL 会映射到宿主机 `127.0.0.1:13306`，避免撞上本机已有的 `3306` MySQL。

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

## 常用命令

```powershell
mvn test
mvn package
node --check src\main\resources\static\app.js
node scripts\verify-design-drift.js --check
```

生成或更新 RCR/质量校验/ADR-002~004：

```powershell
node scripts\verify-design-drift.js --write
```

## 关键接口

- `/api/auth`
- `/api/me`
- `/api/stores`
- `/api/users`
- `/api/orders`
- `/api/quotations`
- `/api/job-tickets`
- `/api/production-tasks`
- `/api/inventory-items`
- `/api/delivery-tasks`
- `/api/invoices`
- `/api/payments`
- `/api/audit-logs`
- `/api/reports`
- `/stats`

## v1 边界

- 文件上传保存到本地 `uploads/`，数据库保存文件元数据。
- 支付、税务、短信/企微、设备/MQTT 使用可替换适配器思路，v1 不接真实第三方。
- Basic Auth 和明文演示密码仅用于课程本地演示，不用于生产。
