package com.printshop;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:printshop_v2_system;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "printshop.upload-dir=target/system-test-uploads"
})
@AutoConfigureMockMvc
class PrintshopV2SystemTests {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clearBusinessData() throws Exception {
        mockMvc.perform(delete("/api/admin/business-data")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldEnforceAuthenticationAndRoleMenus() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"customer\",\"password\":\"demo123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.user.password").doesNotExist());

        mockMvc.perform(get("/api/users").with(httpBasic("customer", "demo123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users").with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(7)))
                .andExpect(jsonPath("$.data[0].password").doesNotExist());
    }

    @Test
    void shouldCoverRoleBasedOrderVisibilityAndInvalidInputs() throws Exception {
        MvcResult customerOrder = createOrder("customer", "论文胶装", "黑白", 10, 1, "到店自提", "普通");
        Integer customerOrderId = JsonPath.read(customerOrder.getResponse().getContentAsString(), "$.data.id");
        String customerOrderNo = JsonPath.read(customerOrder.getResponse().getContentAsString(), "$.data.orderNo");

        MvcResult clerkOrder = createOrder("clerk", "宣传单页", "彩色", 2, 100, "同城配送", "普通");
        Integer clerkOrderId = JsonPath.read(clerkOrder.getResponse().getContentAsString(), "$.data.id");
        String clerkOrderNo = JsonPath.read(clerkOrder.getResponse().getContentAsString(), "$.data.orderNo");

        mockMvc.perform(get("/api/orders").with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(customerOrderNo)))
                .andExpect(content().string(not(containsString(clerkOrderNo))));

        mockMvc.perform(get("/api/orders/{id}", clerkOrderId).with(httpBasic("customer", "demo123")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/orders").with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(customerOrderNo)))
                .andExpect(content().string(containsString(clerkOrderNo)));

        mockMvc.perform(get("/api/orders").with(httpBasic("courier", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(customerOrderNo))));

        progressToDelivery(customerOrderId);

        mockMvc.perform(get("/api/orders").with(httpBasic("courier", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(customerOrderNo)));

        mockMvc.perform(post("/api/orders")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "未知产品",
                                  "colorMode": "黑白",
                                  "pageCount": 1,
                                  "copies": 1,
                                  "deliveryMode": "到店自提",
                                  "priority": "普通"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "论文胶装",
                                  "colorMode": "黑白",
                                  "pageCount": -1,
                                  "copies": 1,
                                  "deliveryMode": "到店自提",
                                  "priority": "普通"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectIllegalWorkflowTransitionsAndMissingResources() throws Exception {
        MvcResult order = createOrder("customer", "培训手册", "黑白", 8, 2, "到店自提", "普通");
        Integer orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/orders/{id}/workflow/quote", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("请先提交审核")));

        mockMvc.perform(post("/api/orders/{id}/status", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PRODUCTION\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/{id}", 999999)
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/payments")
                        .with(httpBasic("clerk", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d,\"amount\":1,\"method\":\"现金\"}".formatted(orderId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/business-data")
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldApproveAndRejectOrderChangeRequests() throws Exception {
        MvcResult approvedOrder = createOrder("customer", "培训手册", "黑白", 20, 3, "到店自提", "普通");
        Integer approvedOrderId = JsonPath.read(approvedOrder.getResponse().getContentAsString(), "$.data.id");
        progressToQuoted(approvedOrderId);

        mockMvc.perform(put("/api/orders/{id}", approvedOrderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "colorMode": "装订加覆膜",
                                  "priority": "加急"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("订单变更请求")));

        MvcResult pending = mockMvc.perform(post("/api/orders/{id}/change-requests", approvedOrderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "培训手册",
                                  "colorMode": "装订加覆膜",
                                  "pageCount": 20,
                                  "copies": 3,
                                  "deliveryMode": "到店自提",
                                  "priority": "加急",
                                  "reason": "客户要求追加覆膜并加急"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        Integer pendingId = JsonPath.read(pending.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/orders/{id}/workflow/job-ticket", approvedOrderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/workflow/production-task", approvedOrderId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("待审批变更")));

        mockMvc.perform(post("/api/order-change-requests/{id}/approve", pendingId)
                        .with(httpBasic("manager", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").value("店长林"));

        mockMvc.perform(get("/api/orders/{id}", approvedOrderId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorMode").value("装订加覆膜"))
                .andExpect(jsonPath("$.data.priority").value("加急"))
                .andExpect(jsonPath("$.data.totalAmount").value(66));

        MvcResult rejectedOrder = createOrder("customer", "论文胶装", "黑白", 10, 1, "到店自提", "普通");
        Integer rejectedOrderId = JsonPath.read(rejectedOrder.getResponse().getContentAsString(), "$.data.id");
        progressToQuoted(rejectedOrderId);
        MvcResult rejected = mockMvc.perform(post("/api/orders/{id}/change-requests", rejectedOrderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "论文胶装",
                                  "colorMode": "彩色",
                                  "pageCount": 10,
                                  "copies": 1,
                                  "deliveryMode": "同城配送",
                                  "priority": "加急",
                                  "reason": "客户希望改彩色配送"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Integer rejectedId = JsonPath.read(rejected.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/order-change-requests/{id}/reject", rejectedId)
                        .with(httpBasic("manager", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"产能不足，驳回\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.freezeEndedAt").isNotEmpty());

        mockMvc.perform(get("/api/orders/{id}", rejectedOrderId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.colorMode").value("黑白"))
                .andExpect(jsonPath("$.data.deliveryMode").value("到店自提"))
                .andExpect(jsonPath("$.data.priority").value("普通"));
    }

    @Test
    void shouldKeepAuditReadonlyAndReportsAvailable() throws Exception {
        MvcResult order = createOrder("customer", "名片快印", "彩色", 2, 50, "到店自提", "普通");
        Integer orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.data.id");
        progressToDelivery(orderId);

        MvcResult audit = mockMvc.perform(get("/api/audit-logs")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(6)))
                .andReturn();
        Integer auditId = JsonPath.read(audit.getResponse().getContentAsString(), "$.data[0].id");

        mockMvc.perform(get("/api/audit-logs/{id}", auditId)
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(auditId));

        mockMvc.perform(delete("/api/audit-logs/{id}", auditId)
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/api/reports")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderFunnel").exists())
                .andExpect(jsonPath("$.data.finance").exists());

        mockMvc.perform(get("/stats")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void shouldUseUnifiedWorkflowWorkbenchAndAggregateOrderView() throws Exception {
        MvcResult order = createOrder("customer", "培训手册", "彩色", 12, 2, "同城配送", "普通");
        Integer orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/workbench/tasks").with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[0].action").value("SUBMIT_REVIEW"))
                .andExpect(jsonPath("$.data.tasks[0].orderId").value(orderId));

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "SUBMIT_REVIEW")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.message").value(containsString("SUBMIT_REVIEW")));

        mockMvc.perform(get("/api/workbench/tasks").with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("QUOTE")));

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "QUOTE")
                        .with(httpBasic("clerk", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("QUOTED"))
                .andExpect(jsonPath("$.data.result.quoteNo").exists())
                .andExpect(jsonPath("$.data.nextTasks").exists());

        mockMvc.perform(get("/api/workbench/tasks").with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks.length()").value(0));

        mockMvc.perform(get("/api/orders/{orderId}/aggregate", orderId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.id").value(orderId))
                .andExpect(jsonPath("$.data.quotations.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.audits").doesNotExist());

        mockMvc.perform(get("/api/orders/{orderId}/aggregate", orderId)
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audits").exists());

        mockMvc.perform(post("/api/orders/{id}/workflow/job-ticket", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "UNKNOWN")
                        .with(httpBasic("admin", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldPreventRepeatedFinanceWorkflowActions() throws Exception {
        MvcResult order = createOrder("customer", "培训手册", "彩色", 12, 2, "同城配送", "普通");
        Integer orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.data.id");
        progressToDelivery(orderId);

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "PAY")
                        .with(httpBasic("finance", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.paymentStatus").value("PAID"));

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "PAY")
                        .with(httpBasic("finance", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "INVOICE")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("WAITING"));

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "INVOICE")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        MvcResult issued = mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "INVOICE")
                        .with(httpBasic("finance", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("ISSUED"))
                .andReturn();
        String issuedAt = JsonPath.read(issued.getResponse().getContentAsString(), "$.data.result.issuedAt");

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "INVOICE")
                        .with(httpBasic("finance", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        Integer invoiceId = JsonPath.read(issued.getResponse().getContentAsString(), "$.data.result.id");
        mockMvc.perform(post("/api/invoices/{id}/issue", invoiceId)
                        .with(httpBasic("finance", "demo123")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/invoices/{id}", invoiceId)
                        .with(httpBasic("finance", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issuedAt").value(issuedAt));

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "REFUND")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("REFUND_REQUESTED"))
                .andExpect(jsonPath("$.data.order.status").value("DELIVERING"));

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "REFUND")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "REFUND")
                        .with(httpBasic("finance", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.order.status").value("REFUNDED"));

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "REFUND")
                        .with(httpBasic("finance", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "PAY")
                        .with(httpBasic("finance", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private MvcResult createOrder(String username, String productType, String colorMode, int pageCount, int copies, String deliveryMode, String priority) throws Exception {
        return mockMvc.perform(post("/api/orders")
                        .with(httpBasic(username, "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "%s",
                                  "colorMode": "%s",
                                  "pageCount": %d,
                                  "copies": %d,
                                  "deliveryMode": "%s",
                                  "priority": "%s",
                                  "totalAmount": 1.00
                                }
                                """.formatted(productType, colorMode, pageCount, copies, deliveryMode, priority)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void progressToQuoted(Integer orderId) throws Exception {
        mockMvc.perform(post("/api/orders/{id}/status", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"step\":\"客户已提交审核\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/{id}/workflow/quote", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());
    }

    private void progressToDelivery(Integer orderId) throws Exception {
        progressToQuoted(orderId);
        mockMvc.perform(post("/api/orders/{id}/workflow/job-ticket", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());
        MvcResult production = mockMvc.perform(post("/api/orders/{id}/workflow/production-task", orderId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andReturn();
        Integer productionId = JsonPath.read(production.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/production-tasks/{id}/complete", productionId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/{id}/workflow/delivery-task", orderId)
                        .with(httpBasic("ops", "demo123")))
                .andExpect(status().isOk());
    }
}
