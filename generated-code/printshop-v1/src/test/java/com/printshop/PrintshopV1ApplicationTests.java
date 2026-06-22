package com.printshop;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:printshop_v1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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

        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/auth/login")));
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
                                  "colorMode": "彩色双面",
                                  "pageCount": 2,
                                  "copies": 200,
                                  "dueAt": "明日 10:00",
                                  "deliveryMode": "到店自提",
                                  "priority": "普通",
                                  "totalAmount": 120.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
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
}
