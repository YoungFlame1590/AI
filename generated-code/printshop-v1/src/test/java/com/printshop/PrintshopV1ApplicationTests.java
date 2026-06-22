package com.printshop;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * v1 工程接口冒烟测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PrintshopV1ApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldServeStaticFrontend() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"roleSelect\"")));

        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/v1/workbench")));

        mockMvc.perform(get("/styles.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("workbench")));
    }

    @Test
    void shouldRunCoreWorkflowAndUpdateStats() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "ORD-TEST-001",
                                  "fileSizeMb": 12.5,
                                  "pageCount": 20,
                                  "paymentStatus": "1已付",
                                  "financialVerifyStatus": "0待核销"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("ORD-TEST-001"))
                .andExpect(jsonPath("$.data.orderStatus").value("待质检"));

        mockMvc.perform(post("/api/v1/quotations/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "ORD-TEST-001",
                                  "discountRate": 0.95,
                                  "finalAmount": 180.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("店长直批"));

        mockMvc.perform(post("/api/v1/productions/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "ORD-TEST-001",
                                  "deviceSn": "DEVICE-01",
                                  "creditLimitUsed": 1200.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceSn").value("DEVICE-01"));

        mockMvc.perform(post("/api/v1/deliveries/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "ORD-TEST-001",
                                  "targetStoreId": "STORE-A",
                                  "financialVerifyStatus": "1已核销",
                                  "outsourcingCostRatio": 12.5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetStoreId").value("STORE-A"));

        mockMvc.perform(post("/api/v1/invoices/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "ORD-TEST-001",
                                  "amount": 180.00,
                                  "triggerMode": "交付后开"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceStatus").value("已开"));

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(5)));

        mockMvc.perform(get("/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.moduleCounts.ORD").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.moduleCounts.QUO").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.moduleCounts.PRO").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.moduleCounts.DLV").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.moduleCounts.FIN").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.moduleCounts.AUD").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void shouldExposeRoleWorkbenchesAndRunActions() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[*].roleId", hasItem("customer")))
                .andExpect(jsonPath("$[*].roleId", hasItem("admin")));

        for (String roleId : new String[]{"customer", "clerk", "manager", "ops", "finance", "courier", "admin"}) {
            mockMvc.perform(get("/api/v1/workbench/{roleId}", roleId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role.roleId").value(roleId))
                    .andExpect(jsonPath("$.metrics.length()", greaterThanOrEqualTo(3)));
        }

        mockMvc.perform(post("/api/v1/workbench/reset?roleId=customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role.roleId").value("customer"))
                .andExpect(jsonPath("$.orders.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/v1/workbench/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": "customer",
                                  "action": "customer_create_order",
                                  "orderId": "",
                                  "payload": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("新建订单完成"))
                .andExpect(jsonPath("$.orders.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.stats.moduleCounts.ORD").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/v1/workbench/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": "manager",
                                  "action": "manager_approve_discount",
                                  "orderId": "ORD-1002",
                                  "payload": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("批准折扣完成"))
                .andExpect(jsonPath("$.orders[?(@.orderId == 'ORD-1002')].quoteStatus", hasItem("折扣已批")))
                .andExpect(jsonPath("$.audits[0].operatorId").value("MGR-201"));
    }
}
