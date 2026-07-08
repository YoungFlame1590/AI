package com.printshop;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:printshop_v2;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "printshop.upload-dir=target/test-uploads"
})
@AutoConfigureMockMvc
class PrintshopV1ApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldServeLoginFrontend() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"loginForm\"")));

        mockMvc.perform(get("/js/main.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/auth/login")));

        mockMvc.perform(get("/js/config.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("orderOptions")));

        mockMvc.perform(get("/vendor/fabric.min.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("fabric")));

        mockMvc.perform(get("/vendor/qrcode.min.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("QRCode")));
    }

    @Test
    void shouldEnforceOrderVisibilityOptionsAndServerPricing() throws Exception {
        mockMvc.perform(get("/api/orders").with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ORD-20260618"))));

        MvcResult staffOrder = mockMvc.perform(post("/api/orders")
                        .with(httpBasic("clerk", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "外部客户不应生效",
                                  "productType": "宣传单页",
                                  "colorMode": "黑白",
                                  "pageCount": 1,
                                  "copies": 100,
                                  "deliveryMode": "同城配送",
                                  "priority": "加急",
                                  "totalAmount": 1.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerName").value("前台小周"))
                .andExpect(jsonPath("$.data.totalAmount").value(41.4))
                .andReturn();
        Integer staffOrderId = JsonPath.read(staffOrder.getResponse().getContentAsString(), "$.data.id");
        String staffOrderNo = JsonPath.read(staffOrder.getResponse().getContentAsString(), "$.data.orderNo");

        mockMvc.perform(get("/api/orders/{id}", staffOrderId).with(httpBasic("customer", "demo123")))
                .andExpect(status().isNotFound());

        MvcResult customerEditableOrder = mockMvc.perform(post("/api/orders")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "论文胶装",
                                  "colorMode": "黑白",
                                  "pageCount": 5,
                                  "copies": 1,
                                  "deliveryMode": "到店自提",
                                  "priority": "普通"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Integer customerEditableOrderId = JsonPath.read(customerEditableOrder.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/orders/{id}", customerEditableOrderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "论文胶装",
                                  "colorMode": "黑白",
                                  "pageCount": 10,
                                  "copies": 1,
                                  "deliveryMode": "到店自提",
                                  "priority": "普通",
                                  "totalAmount": 1.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(19.2));

        MvcResult courierVisibleOrder = mockMvc.perform(post("/api/orders")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "海报写真",
                                  "colorMode": "彩色",
                                  "pageCount": 2,
                                  "copies": 3,
                                  "deliveryMode": "外协配送",
                                  "priority": "普通"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Integer courierOrderId = JsonPath.read(courierVisibleOrder.getResponse().getContentAsString(), "$.data.id");
        String courierOrderNo = JsonPath.read(courierVisibleOrder.getResponse().getContentAsString(), "$.data.orderNo");

        uploadOrderFile(courierOrderId);
        mockMvc.perform(post("/api/orders/{id}/status", courierOrderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"step\":\"客户已提交审核\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/{id}/workflow/quote", courierOrderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());
        confirmQuote(courierOrderId);
        mockMvc.perform(post("/api/orders/{id}/workflow/job-ticket", courierOrderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());
        MvcResult courierProduction = mockMvc.perform(post("/api/orders/{id}/workflow/production-task", courierOrderId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andReturn();
        Integer courierProductionId = JsonPath.read(courierProduction.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/production-tasks/{id}/complete", courierProductionId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/delivery-tasks")
                        .with(httpBasic("ops", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "mode": "外协配送",
                                  "carrierName": "配送赵",
                                  "targetStore": "客户地址"
                                }
                                """.formatted(courierOrderId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders").with(httpBasic("courier", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(courierOrderNo)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(staffOrderNo))));

        mockMvc.perform(post("/api/orders")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "未知产品",
                                  "colorMode": "彩色",
                                  "pageCount": 1,
                                  "copies": 1,
                                  "deliveryMode": "到店自提",
                                  "priority": "普通"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/orders/{id}", courierOrderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PRODUCTION\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRunPrintMisLifecycle() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"demo123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        mockMvc.perform(get("/api/me/dashboard").with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.length()", greaterThanOrEqualTo(4)));

        mockMvc.perform(get("/api/users").with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].password").doesNotExist());

        MvcResult orderResult = mockMvc.perform(post("/api/orders")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "名片快印",
                                  "colorMode": "彩色",
                                  "pageCount": 2,
                                  "copies": 200,
                                  "deliveryMode": "到店自提",
                                  "priority": "普通",
                                  "totalAmount": 1.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.customerName").value("张同学"))
                .andExpect(jsonPath("$.data.dueAt").isNotEmpty())
                .andExpect(jsonPath("$.data.totalAmount").value(200.0))
                .andReturn();
        Integer orderId = JsonPath.read(orderResult.getResponse().getContentAsString(), "$.data.id");

        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "business-card.pdf",
                "application/pdf",
                "demo file".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(upload)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileStatus").value("UPLOADED"));

        mockMvc.perform(post("/api/orders/{id}/status", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"step\":\"客户已提交审核\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWING"));

        MvcResult quoteResult = mockMvc.perform(post("/api/quotations")
                        .with(httpBasic("clerk", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "subtotal": 150.00,
                                  "discountRate": 0.90,
                                  "finalAmount": 135.00,
                                  "validUntil": "2026-06-30"
                                }
                                """.formatted(orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andReturn();
        Integer quotationId = JsonPath.read(quoteResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/quotations/{id}/approve", quotationId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        MvcResult jobResult = mockMvc.perform(post("/api/job-tickets")
                        .with(httpBasic("clerk", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "quotationId": %d,
                                  "specs": "名片 200 张，覆膜",
                                  "paperType": "300g铜版纸",
                                  "binding": "裁切"
                                }
                                """.formatted(orderId, quotationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andReturn();
        Integer jobTicketId = JsonPath.read(jobResult.getResponse().getContentAsString(), "$.data.id");

        MvcResult productionResult = mockMvc.perform(post("/api/production-tasks")
                        .with(httpBasic("manager", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobTicketId": %d,
                                  "station": "数码印刷-02",
                                  "operatorName": "生产吴",
                                  "plannedStart": "今日 14:00",
                                  "plannedEnd": "今日 16:00"
                                }
                                """.formatted(jobTicketId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andReturn();
        Integer productionTaskId = JsonPath.read(productionResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/production-tasks/{id}/complete", productionTaskId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qualityStatus").value("PASS"));

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PRODUCTION_DONE"));

        MvcResult inventoryResult = mockMvc.perform(post("/api/inventory-items")
                        .with(httpBasic("ops", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "CARD-300G",
                                  "itemName": "300g铜版纸",
                                  "category": "纸张",
                                  "unit": "包",
                                  "quantity": 8,
                                  "safetyStock": 5,
                                  "location": "大学城店"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Integer inventoryId = JsonPath.read(inventoryResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/inventory-items/{id}/adjust", inventoryId)
                        .with(httpBasic("ops", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(18));

        MvcResult deliveryResult = mockMvc.perform(post("/api/delivery-tasks")
                        .with(httpBasic("ops", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "mode": "到店自提",
                                  "carrierName": "配送赵",
                                  "targetStore": "大学城店"
                                }
                                """.formatted(orderId)))
                .andExpect(status().isOk())
                .andReturn();
        Integer deliveryId = JsonPath.read(deliveryResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/delivery-tasks/{id}/sign", deliveryId)
                        .with(httpBasic("courier", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signedBy\":\"张同学\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SIGNED"));

        MvcResult paymentResult = mockMvc.perform(post("/api/payments")
                        .with(httpBasic("finance", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "amount": 135.00,
                                  "method": "微信"
                                }
                                """.formatted(orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn();
        Integer paymentId = JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.data.id");

        MvcResult invoiceResult = mockMvc.perform(post("/api/invoices")
                        .with(httpBasic("finance", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "title": "张同学",
                                  "amount": 135.00
                                }
                                """.formatted(orderId)))
                .andExpect(status().isOk())
                .andReturn();
        Integer invoiceId = JsonPath.read(invoiceResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/invoices/{id}/issue", invoiceId)
                        .with(httpBasic("finance", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ISSUED"));

        mockMvc.perform(post("/api/payments/{id}/refund", paymentId)
                        .with(httpBasic("finance", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        MvcResult auditResult = mockMvc.perform(get("/api/audit-logs")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(8)))
                .andReturn();
        Integer auditId = JsonPath.read(auditResult.getResponse().getContentAsString(), "$.data[0].id");
        mockMvc.perform(get("/api/audit-logs/{id}", auditId)
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(auditId));

        mockMvc.perform(get("/api/reports")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderFunnel").exists());

        mockMvc.perform(get("/stats")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(greaterThanOrEqualTo(10)));
    }

    @Test
    void shouldSupportGuidedOrderWorkflowAndAdminClear() throws Exception {
        mockMvc.perform(delete("/api/admin/business-data")
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/business-data")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("业务数据已清空，基础门店、七类演示账号和默认库存已保留。"));

        MvcResult orderResult = mockMvc.perform(post("/api/orders")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "培训手册",
                                  "colorMode": "黑白加彩页",
                                  "pageCount": 40,
                                  "copies": 6,
                                  "deliveryMode": "同城配送",
                                  "priority": "普通"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerName").value("张同学"))
                .andReturn();
        Integer orderId = JsonPath.read(orderResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/orders/{id}/workflow/quote", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("请先提交审核")));

        uploadOrderFile(orderId);
        mockMvc.perform(post("/api/orders/{id}/status", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"step\":\"客户已提交审核\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWING"));

        mockMvc.perform(post("/api/orders/{id}/workflow/quote", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("SENT"));
        confirmQuote(orderId);

        mockMvc.perform(post("/api/orders/{id}/workflow/job-ticket", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("READY"));

        MvcResult productionResult = mockMvc.perform(post("/api/orders/{id}/workflow/production-task", orderId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andReturn();
        Integer productionId = JsonPath.read(productionResult.getResponse().getContentAsString(), "$.data.id");

        BigDecimal paperBefore = inventoryQuantity("PAPER-A4-80G");
        mockMvc.perform(post("/api/production-tasks/{id}/complete", productionId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));
        BigDecimal paperAfter = inventoryQuantity("PAPER-A4-80G");
        org.assertj.core.api.Assertions.assertThat(paperAfter).isLessThan(paperBefore);

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PRODUCTION_DONE"));

        MvcResult quoteResult = mockMvc.perform(post("/api/delivery-quotes")
                        .with(httpBasic("ops", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "channelCode": "IMMEDIATE",
                                  "pickupAddress": "大学城店",
                                  "deliveryAddress": "广州市大学城客户公司前台",
                                  "packageWeightKg": 1.5
                                }
                                """.formatted(orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andReturn();
        Integer quoteId = JsonPath.read(quoteResult.getResponse().getContentAsString(), "$.data.id");

        MvcResult deliveryResult = mockMvc.perform(post("/api/delivery-quotes/{id}/confirm", quoteId)
                        .with(httpBasic("ops", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andReturn();
        Integer deliveryId = JsonPath.read(deliveryResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/orders/{id}/workflow/delivery-task", orderId)
                        .with(httpBasic("courier", "demo123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/orders/{id}/workflow/accept-delivery", orderId)
                        .with(httpBasic("courier", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carrierName").value("配送赵"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        mockMvc.perform(post("/api/orders/{id}/workflow/payment", orderId)
                        .with(httpBasic("finance", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(httpBasic("finance", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERING"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        mockMvc.perform(post("/api/orders/{id}/workflow/invoice", orderId)
                        .with(httpBasic("finance", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.taxNo").value("个人无需税号"));

        mockMvc.perform(post("/api/delivery-tasks/{id}/sign", deliveryId)
                        .with(httpBasic("courier", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signedBy\":\"张同学\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SIGNED"));

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));

        mockMvc.perform(post("/api/orders/{id}/workflow/refund", orderId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUND_REQUESTED"));

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        mockMvc.perform(post("/api/orders/{id}/workflow/refund", orderId)
                        .with(httpBasic("finance", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));

        mockMvc.perform(post("/api/orders/{id}/workflow/payment", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(delete("/api/admin/business-data")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted.orders", greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/orders")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/audit-logs")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void shouldFreezeOrderWhenProcessedOrderChangeIsRequested() throws Exception {
        mockMvc.perform(delete("/api/admin/business-data")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk());

        MvcResult orderResult = mockMvc.perform(post("/api/orders")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "培训手册",
                                  "colorMode": "黑白",
                                  "pageCount": 20,
                                  "copies": 3,
                                  "deliveryMode": "到店自提",
                                  "priority": "普通"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Integer orderId = JsonPath.read(orderResult.getResponse().getContentAsString(), "$.data.id");

        uploadOrderFile(orderId);
        mockMvc.perform(post("/api/orders/{id}/status", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"step\":\"客户已提交审核\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/workflow/quote", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());
        confirmQuote(orderId);

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "培训手册",
                                  "colorMode": "装订加覆膜",
                                  "pageCount": 30,
                                  "copies": 3,
                                  "deliveryMode": "同城配送",
                                  "priority": "加急"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("订单变更请求")));

        mockMvc.perform(post("/api/orders/{id}/change-requests", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "培训手册",
                                  "colorMode": "装订加覆膜",
                                  "pageCount": 30,
                                  "copies": 3,
                                  "deliveryMode": "同城配送",
                                  "priority": "加急",
                                  "reason": "客户追加覆膜并要求加急配送"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.newColorMode").value("装订加覆膜"));

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorMode").value("黑白"))
                .andExpect(jsonPath("$.data.currentStep").value(org.hamcrest.Matchers.containsString("待审批订单变更")));

        MvcResult changeList = mockMvc.perform(get("/api/order-change-requests")
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].newColorMode").value("装订加覆膜"))
                .andReturn();
        Integer changeId = JsonPath.read(changeList.getResponse().getContentAsString(), "$.data[0].id");

        mockMvc.perform(post("/api/orders/{id}/workflow/job-ticket", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/workflow/production-task", orderId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("待审批变更")));

        mockMvc.perform(post("/api/order-change-requests/{id}/approve", changeId)
                        .with(httpBasic("manager", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"批准追加覆膜与加急\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorMode").value("装订加覆膜"))
                .andExpect(jsonPath("$.data.pageCount").value(30))
                .andExpect(jsonPath("$.data.deliveryMode").value("同城配送"))
                .andExpect(jsonPath("$.data.priority").value("加急"))
                .andExpect(jsonPath("$.data.currentStep").value(org.hamcrest.Matchers.containsString("变更已批准")));

        mockMvc.perform(post("/api/orders/{id}/workflow/production-task", orderId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    private BigDecimal inventoryQuantity(String sku) throws Exception {
        MvcResult inventory = mockMvc.perform(get("/api/inventory-items")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> items = JsonPath.read(inventory.getResponse().getContentAsString(), "$.data");
        return items.stream()
                .filter(item -> sku.equals(item.get("sku")))
                .findFirst()
                .map(item -> new BigDecimal(String.valueOf(item.get("quantity"))))
                .orElseThrow();
    }

    private void confirmQuote(Integer orderId) throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "CONFIRM_QUOTE")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private void uploadOrderFile(Integer orderId) throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "order-" + orderId + ".pdf",
                "application/pdf",
                "print-ready".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(upload)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk());
    }
}
