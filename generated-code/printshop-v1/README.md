# PrintShop v1 Spring Boot 工程

本工程是“图文快印门店连锁管理系统”的 v1 课程演示版，依据需求基线、TLCD 三层约束、OpenAPI 契约和约束提示词生成。

## 运行

```powershell
mvn spring-boot:run
```

默认地址：

```text
http://127.0.0.1:8080
```

七类角色业务工作台：

```text
http://127.0.0.1:8080/
```

页面顶部可切换客户、门店店员、门店店长、总部运营管理员、财务人员、配送/外协人员和系统管理员。每个角色有独立指标、待办、订单队列、详情动作、审计日志和 `/stats` 统计。

## 验证

```powershell
mvn test
mvn package
```

## 已实现接口

- `POST /api/v1/orders`
- `POST /api/v1/quotations/calculate`
- `POST /api/v1/productions/dispatch`
- `POST /api/v1/deliveries/route`
- `POST /api/v1/invoices/issue`
- `GET /api/v1/audit-logs`
- `GET /stats`

## 角色工作台接口

- `GET /api/v1/roles`
- `GET /api/v1/workbench/{roleId}`
- `POST /api/v1/workbench/actions`
- `POST /api/v1/workbench/reset`

## 前端资源

- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/main/resources/static/app.js`

## v1 简化

- 使用内存 Repository，不接入数据库。
- 角色工作台使用内存种子数据，点击“重置数据”或重启应用后恢复演示状态。
- 支付、税务、机台接口为 INFRA 防腐层占位适配器。
- `/stats` 进程重启后清零。
