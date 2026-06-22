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

前端演示台：

```text
http://127.0.0.1:8080/
```

页面支持单独调用 6 个业务接口，也支持“一键演示完整流程”。

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

## 前端资源

- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/main/resources/static/app.js`

## v1 简化

- 使用内存 Repository，不接入数据库。
- 支付、税务、机台接口为 INFRA 防腐层占位适配器。
- `/stats` 进程重启后清零。
