package com.printshop;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误。"));

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
    void shouldRegisterCustomersAndLetAdminManageUsersAndStores() throws Exception {
        String customerName = "student_" + System.nanoTime();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "demo123",
                                  "displayName": "新客户"
                                }
                                """.formatted(customerName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.user.storeId").exists())
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        mockMvc.perform(get("/api/orders").with(httpBasic(customerName, "demo123")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"demo123\",\"displayName\":\"重复\"}".formatted(customerName)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s_2\",\"password\":\"demo123\",\"displayName\":\"新客户\"}".formatted(customerName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.displayName").value("新客户"));

        String storeCode = "STORE-T-" + Long.toString(System.nanoTime(), 36).toUpperCase();
        MvcResult store = mockMvc.perform(post("/api/admin/stores")
                        .with(httpBasic("admin", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "测试门店",
                                  "address": "测试路 1 号",
                                  "phone": "020-3000000",
                                  "active": true
                                }
                                """.formatted(storeCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        Integer storeId = JsonPath.read(store.getResponse().getContentAsString(), "$.data.id");

        String clerkName = "clerk_" + System.nanoTime();
        MvcResult clerk = mockMvc.perform(post("/api/admin/users")
                        .with(httpBasic("admin", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "demo123",
                                  "role": "CLERK",
                                  "displayName": "测试前台",
                                  "storeId": %d,
                                  "active": true
                                }
                                """.formatted(clerkName, storeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.role").value("CLERK"))
                .andReturn();
        Integer clerkId = JsonPath.read(clerk.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/admin/users/{id}", clerkId)
                        .with(httpBasic("admin", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"新客户\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("新客户"));

        mockMvc.perform(post("/api/admin/users/{id}/reset-password", clerkId)
                        .with(httpBasic("admin", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"demo456\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders").with(httpBasic(clerkName, "demo456")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/users/{id}", clerkId)
                        .with(httpBasic("admin", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        mockMvc.perform(get("/api/orders").with(httpBasic(clerkName, "demo456")))
                .andExpect(status().isUnauthorized());

        MvcResult currentAdmin = mockMvc.perform(get("/api/me").with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andReturn();
        Integer adminId = JsonPath.read(currentAdmin.getResponse().getContentAsString(), "$.data.user.id");
        mockMvc.perform(delete("/api/admin/users/{id}", adminId).with(httpBasic("admin", "demo123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("当前登录账号")));
        mockMvc.perform(put("/api/admin/users/{id}", adminId)
                        .with(httpBasic("admin", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("当前登录账号")));
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

        mockMvc.perform(post("/api/orders")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "论文胶装",
                                  "colorMode": "黑白",
                                  "pageCount": 1,
                                  "copies": 10001,
                                  "deliveryMode": "到店自提",
                                  "priority": "普通"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("份数不能超过")));
    }

    @Test
    void shouldScopeBusinessDataByStoreForBranchRoles() throws Exception {
        String branchClerk = "branch_clerk_" + System.nanoTime();
        mockMvc.perform(post("/api/admin/users")
                        .with(httpBasic("admin", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "demo123",
                                  "role": "CLERK",
                                  "displayName": "二店前台",
                                  "storeId": 2,
                                  "active": true
                                }
                                """.formatted(branchClerk)))
                .andExpect(status().isOk());

        MvcResult branchOrder = createOrder(branchClerk, "宣传单页", "彩色", 2, 20, "到店自提", "普通");
        Integer branchOrderId = JsonPath.read(branchOrder.getResponse().getContentAsString(), "$.data.id");
        String branchOrderNo = JsonPath.read(branchOrder.getResponse().getContentAsString(), "$.data.orderNo");

        mockMvc.perform(get("/api/orders").with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(branchOrderNo))));

        mockMvc.perform(get("/api/orders/{id}", branchOrderId).with(httpBasic("manager", "demo123")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/orders").with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(branchOrderNo)));

        mockMvc.perform(get("/api/reports").with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("市中心店"))));
    }

    @Test
    void shouldRequireQuoteConfirmationAndBlockProductionWhenInventoryIsInsufficient() throws Exception {
        MvcResult order = createOrder("customer", "论文胶装", "黑白", 20, 3, "到店自提", "普通");
        Integer orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.data.id");
        uploadOrderFile(orderId, "customer");

        mockMvc.perform(post("/api/orders/{id}/status", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"step\":\"客户已提交审核\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/{id}/workflow/quote", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/workflow/job-ticket", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("客户确认报价")));

        confirmQuote(orderId);
        mockMvc.perform(post("/api/orders/{id}/workflow/job-ticket", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());

        Integer paperId = inventoryItemId("PAPER-A4-80G");
        BigDecimal paperQuantity = inventoryQuantity("PAPER-A4-80G");
        mockMvc.perform(post("/api/inventory-items/{id}/adjust", paperId)
                        .with(httpBasic("admin", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-%s}".formatted(paperQuantity.toPlainString())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/workflow/production-task", orderId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("库存不足")));
    }

    @Test
    void shouldValidateUploadAndStreamPreviewAndDownloadFiles() throws Exception {
        MvcResult order = createOrder("customer", "论文胶装", "黑白", 10, 1, "到店自提", "普通");
        Integer orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.data.id");

        MockMultipartFile pdf = new MockMultipartFile(
                "file",
                "design.pdf",
                "application/pdf",
                "%PDF-1.4 demo".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        MvcResult uploaded = mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(pdf)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.uploadedBy").value("张同学"))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.analysisStatus").value("FAILED"))
                .andReturn();
        Integer fileId = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.data.id");
        String filePath = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.data.filePath");

        mockMvc.perform(get("/api/order-files/{fileId}/preview", fileId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/order-files/{fileId}/download", fileId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));

        MockMultipartFile docx = new MockMultipartFile(
                "file",
                "brief.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        MvcResult docxUpload = mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(docx)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.analysisStatus").value("FAILED"))
                .andReturn();
        Integer docxFileId = JsonPath.read(docxUpload.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(get("/api/order-files/{fileId}/preview", docxFileId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("暂不支持在线预览")));

        MockMultipartFile txt = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "text".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(txt)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isBadRequest());

        Files.deleteIfExists(Path.of(filePath));
        mockMvc.perform(get("/api/order-files/{fileId}/download", fileId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAnalyzeFilesAndUpdateOnlySubmittedOrders() throws Exception {
        MvcResult order = createOrder("customer", "培训手册", "黑白", 7, 2, "到店自提", "普通");
        Integer orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.data.id");

        MockMultipartFile mixedPdf = new MockMultipartFile(
                "file",
                "mixed-pages.pdf",
                "application/pdf",
                pdfBytes(PDRectangle.A4, PDRectangle.A5, PDRectangle.A5)
        );
        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(mixedPdf)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisStatus").value("DETECTED"))
                .andExpect(jsonPath("$.data.detectedPageCount").value(3))
                .andExpect(jsonPath("$.data.detectedWidthMm").value(210.0))
                .andExpect(jsonPath("$.data.detectedHeightMm").value(297.0))
                .andExpect(jsonPath("$.data.mixedPageSizes").value(true))
                .andExpect(jsonPath("$.data.analysisMessage").value(containsString("金额已重新计算")));

        mockMvc.perform(get("/api/orders/{id}", orderId).with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageCount").value(3))
                .andExpect(jsonPath("$.data.currentStep").value(containsString("已识别 3 页")));

        MockMultipartFile png = new MockMultipartFile(
                "file",
                "poster.png",
                "image/png",
                pngBytes(320, 240, 96)
        );
        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(png)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisStatus").value("DETECTED"))
                .andExpect(jsonPath("$.data.detectedPageCount").value(1))
                .andExpect(jsonPath("$.data.detectedPixelWidth").value(320))
                .andExpect(jsonPath("$.data.detectedPixelHeight").value(240))
                .andExpect(jsonPath("$.data.detectedDpiX").exists())
                .andExpect(jsonPath("$.data.detectedDpiY").exists());

        MockMultipartFile unsupported = new MockMultipartFile(
                "file",
                "source.psd",
                "image/vnd.adobe.photoshop",
                "office".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(unsupported)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisStatus").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.data.detectedPageCount").doesNotExist());

        MockMultipartFile brokenPdf = new MockMultipartFile(
                "file",
                "broken.pdf",
                "application/pdf",
                "not-a-pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(brokenPdf)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.analysisMessage").value(containsString("文件分析失败")));

        mockMvc.perform(post("/api/orders/{id}/status", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"step\":\"客户已提交审核\"}"))
                .andExpect(status().isOk());

        MockMultipartFile reviewedPdf = new MockMultipartFile(
                "file",
                "reviewed-version.pdf",
                "application/pdf",
                pdfBytes(PDRectangle.A4, PDRectangle.A4, PDRectangle.A4, PDRectangle.A4)
        );
        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(reviewedPdf)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detectedPageCount").value(4))
                .andExpect(jsonPath("$.data.analysisMessage").value(containsString("未自动修改")));

        mockMvc.perform(get("/api/orders/{id}", orderId).with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageCount").value(1));
    }

    @Test
    void shouldCreateOrderFromWordFileAndHandlePartialOrBrokenWord() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "file",
                "empty.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[0]
        );
        mockMvc.perform(multipart("/api/orders/from-file")
                        .file(empty)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("不能为空")));

        MockMultipartFile docx = new MockMultipartFile(
                "file",
                "manual.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes(12, true)
        );
        MvcResult created = mockMvc.perform(multipart("/api/orders/from-file")
                        .file(docx)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.order.pageCount").value(12))
                .andExpect(jsonPath("$.data.file.versionNo").value(1))
                .andExpect(jsonPath("$.data.file.analysisStatus").value("DETECTED"))
                .andExpect(jsonPath("$.data.file.detectedPageCount").value(12))
                .andExpect(jsonPath("$.data.file.detectedWidthMm").value(210.01))
                .andExpect(jsonPath("$.data.file.detectedHeightMm").value(297.0))
                .andExpect(jsonPath("$.data.file.mixedPageSizes").value(true))
                .andExpect(jsonPath("$.data.file.analysisMessage").value(containsString("文档保存属性")))
                .andReturn();
        Integer orderId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.order.id");

        MockMultipartFile partialDocx = new MockMultipartFile(
                "file",
                "no-page-count.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes(null, false)
        );
        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(partialDocx)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.data.detectedPageCount").doesNotExist())
                .andExpect(jsonPath("$.data.detectedWidthMm").value(210.01));

        MockMultipartFile brokenDoc = new MockMultipartFile(
                "file",
                "legacy.doc",
                "application/msword",
                "not-a-word-document".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(brokenDoc)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.analysisMessage").value(containsString("文件分析失败")));

        mockMvc.perform(get("/api/orders/{id}/files", orderId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void shouldRecordOverLimitPdfWithoutUpdatingOrder() throws Exception {
        MvcResult order = createOrder("customer", "培训手册", "黑白", 7, 1, "到店自提", "普通");
        Integer orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.data.id");
        MockMultipartFile pdf = new MockMultipartFile(
                "file",
                "too-many-pages.pdf",
                "application/pdf",
                pdfBytes(5001, PDRectangle.A6)
        );

        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(pdf)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisStatus").value("DETECTED"))
                .andExpect(jsonPath("$.data.detectedPageCount").value(5001))
                .andExpect(jsonPath("$.data.analysisMessage").value(containsString("超出订单上限")));

        mockMvc.perform(get("/api/orders/{id}", orderId).with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageCount").value(7));
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
    void shouldFreezeProductionCompletionAndDeliveryCreationForPendingChanges() throws Exception {
        MvcResult inProductionOrder = createOrder("customer", "培训手册", "黑白", 12, 2, "到店自提", "普通");
        Integer inProductionOrderId = JsonPath.read(inProductionOrder.getResponse().getContentAsString(), "$.data.id");
        progressToQuoted(inProductionOrderId);
        mockMvc.perform(post("/api/orders/{id}/workflow/job-ticket", inProductionOrderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());
        MvcResult production = mockMvc.perform(post("/api/orders/{id}/workflow/production-task", inProductionOrderId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andReturn();
        Integer productionId = JsonPath.read(production.getResponse().getContentAsString(), "$.data.id");
        createChangeRequest(inProductionOrderId);

        mockMvc.perform(post("/api/production-tasks/{id}/complete", productionId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("待审批变更")));

        MvcResult productionDoneOrder = createOrder("customer", "论文胶装", "黑白", 8, 1, "同城配送", "普通");
        Integer productionDoneOrderId = JsonPath.read(productionDoneOrder.getResponse().getContentAsString(), "$.data.id");
        progressToQuoted(productionDoneOrderId);
        mockMvc.perform(post("/api/orders/{id}/workflow/job-ticket", productionDoneOrderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());
        MvcResult secondProduction = mockMvc.perform(post("/api/orders/{id}/workflow/production-task", productionDoneOrderId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andReturn();
        Integer secondProductionId = JsonPath.read(secondProduction.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/production-tasks/{id}/complete", secondProductionId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk());
        createChangeRequest(productionDoneOrderId);

        mockMvc.perform(post("/api/orders/{id}/workflow/delivery-task", productionDoneOrderId)
                        .with(httpBasic("ops", "demo123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("待审批变更")));
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
                .andExpect(jsonPath("$.data.tasks[0].action").value("UPLOAD_FILE"))
                .andExpect(jsonPath("$.data.tasks[0].orderId").value(orderId));

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "SUBMIT_REVIEW")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先上传订单文件，再提交审核。"));

        mockMvc.perform(post("/api/orders/{id}/status", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先上传订单文件，再提交审核。"));

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .with(httpBasic("admin", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先上传订单文件，再提交审核。"));

        uploadOrderFile(orderId, "customer");
        mockMvc.perform(get("/api/workbench/tasks").with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[0].action").value("SUBMIT_REVIEW"));

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

        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "CONFIRM_QUOTE")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("CUSTOMER_CONFIRMED"));

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

    @Test
    void shouldCreateDesignProjectVersionAndSubmitOrderFromTemplate() throws Exception {
        MvcResult templates = mockMvc.perform(get("/api/design-templates")
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andReturn();
        Integer templateId = JsonPath.read(templates.getResponse().getContentAsString(), "$.data[0].id");
        String templateSizeName = JsonPath.read(templates.getResponse().getContentAsString(), "$.data[0].sizeName");

        MvcResult project = mockMvc.perform(post("/api/design-projects")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": %d,
                                  "title": "测试在线设计",
                                  "canvasJson": "{\\"objects\\":[{\\"type\\":\\"text\\",\\"text\\":\\"测试名片\\",\\"x\\":80,\\"y\\":80}]}"
                                }
                                """.formatted(templateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.versions.length()").value(1))
                .andReturn();
        Integer projectId = JsonPath.read(project.getResponse().getContentAsString(), "$.data.project.id");

        String largeCanvasJson = "{\"version\":\"5.3.0\",\"objects\":[{\"type\":\"textbox\",\"text\":\""
                + "A".repeat(6200)
                + "\",\"left\":40,\"top\":60}]}";
        mockMvc.perform(post("/api/design-projects/{id}/versions", projectId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "大画布容量",
                                  "canvasJson": "%s"
                                }
                                """.formatted(jsonEscape(largeCanvasJson))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.currentVersionNo").value(2))
                .andExpect(jsonPath("$.data.project.canvasJson").value(containsString("AAAA")));

        mockMvc.perform(post("/api/design-projects/{id}/versions", projectId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "替换Logo",
                                  "canvasJson": "{\\"objects\\":[{\\"type\\":\\"logo\\",\\"text\\":\\"ACME\\",\\"x\\":110,\\"y\\":90}]}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.currentVersionNo").value(3));

        mockMvc.perform(post("/api/design-projects/{id}/restore/{versionNo}", projectId, 1)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.currentVersionNo").value(1));

        MvcResult submitted = mockMvc.perform(post("/api/design-projects/{id}/submit-order", projectId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "copies": 120,
                                  "deliveryMode": "到店自提",
                                  "priority": "普通",
                                  "paperType": "铜版纸250g",
                                  "craftType": "覆膜+压痕"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.order.id").exists())
                .andExpect(jsonPath("$.data.order.sizeName").value(templateSizeName))
                .andExpect(jsonPath("$.data.order.paperType").value("铜版纸250g"))
                .andExpect(jsonPath("$.data.order.craftType").value("覆膜+压痕"))
                .andExpect(jsonPath("$.data.file.fileStatus").value("GENERATED"))
                .andReturn();
        Integer orderId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.order.id");

        mockMvc.perform(get("/api/orders/{id}/files", orderId)
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].analysisStatus").value("GENERATED"));
    }

    @Test
    void shouldGenerateDynamicReplenishmentAndApprovePurchaseSuggestion() throws Exception {
        MvcResult order = createOrder("customer", "培训手册", "彩色", 20, 4, "到店自提", "普通");
        Integer orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.data.id");
        progressToDelivery(orderId);

        Integer paperId = inventoryItemId("PAPER-A4-80G");
        BigDecimal paperQuantity = inventoryQuantity("PAPER-A4-80G");
        mockMvc.perform(post("/api/inventory-items/{id}/adjust", paperId)
                        .with(httpBasic("admin", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-%s}".formatted(paperQuantity.toPlainString())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/replenishment/recommendations")
                        .with(httpBasic("ops", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dynamicSafetyStock")));

        mockMvc.perform(get("/api/replenishment/forecast")
                        .with(httpBasic("ops", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("forecastNext30Days")))
                .andExpect(content().string(containsString("averageMonthlyGrowthRate")));

        MvcResult suggestions = mockMvc.perform(post("/api/replenishment/recalculate")
                        .with(httpBasic("ops", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andReturn();
        Integer suggestionId = JsonPath.read(suggestions.getResponse().getContentAsString(), "$.data[0].id");

        mockMvc.perform(post("/api/purchase-suggestions/{id}/approve", suggestionId)
                        .with(httpBasic("ops", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void shouldQuoteThirdPartyDeliveryTrackAndCreateFeedbackComplaint() throws Exception {
        MvcResult order = createOrder("customer", "宣传单页", "彩色", 2, 50, "同城配送", "普通");
        Integer orderId = JsonPath.read(order.getResponse().getContentAsString(), "$.data.id");
        progressToProductionDone(orderId);

        MvcResult quote = mockMvc.perform(post("/api/delivery-quotes")
                        .with(httpBasic("ops", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "channelCode": "IMMEDIATE",
                                  "pickupAddress": "大学城店",
                                  "deliveryAddress": "客户公司前台",
                                  "packageWeightKg": 1.5
                                }
                                """.formatted(orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedFee").exists())
                .andReturn();
        Integer quoteId = JsonPath.read(quote.getResponse().getContentAsString(), "$.data.id");

        MvcResult task = mockMvc.perform(post("/api/delivery-quotes/{id}/confirm", quoteId)
                        .with(httpBasic("ops", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingNo").exists())
                .andReturn();
        Integer taskId = JsonPath.read(task.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/delivery-tasks/{id}/sync-tracking", taskId)
                        .with(httpBasic("ops", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()", greaterThanOrEqualTo(2)));

        mockMvc.perform(post("/api/delivery-tasks/{id}/sign", taskId)
                        .with(httpBasic("ops", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signedBy\":\"客户签收\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/service-review-invitations")
                        .with(httpBasic("customer", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PENDING")));

        mockMvc.perform(post("/api/orders/{orderId}/service-reviews", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "printQualityRating": 2,
                                  "timelinessRating": 2,
                                  "staffRating": 1,
                                  "valueRating": 2,
                                  "comment": "配送延误且沟通不足"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallRating").value(2));

        MvcResult complaints = mockMvc.perform(get("/api/complaint-tickets")
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andReturn();
        Integer complaintId = JsonPath.read(complaints.getResponse().getContentAsString(), "$.data[0].id");

        mockMvc.perform(post("/api/complaint-tickets/{id}/reply", complaintId)
                        .with(httpBasic("manager", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"已联系客户重印并补偿配送费\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REPLIED"));

        mockMvc.perform(post("/api/complaint-tickets/{id}/close", complaintId)
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(get("/api/reports/store-quality-ranking")
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("averageRating")))
                .andExpect(content().string(containsString("negativeRate")));

        mockMvc.perform(get("/api/reports")
                        .with(httpBasic("manager", "demo123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("storeQualityRanking")));
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

    private String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    private byte[] pdfBytes(PDRectangle... pageSizes) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (PDRectangle pageSize : pageSizes) {
                document.addPage(new PDPage(pageSize));
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] pdfBytes(int pageCount, PDRectangle pageSize) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pageCount; index++) {
                document.addPage(new PDPage(pageSize));
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] docxBytes(Integer pageCount, boolean mixedPageSizes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Print MIS Word analysis fixture");
            if (pageCount != null) {
                document.getProperties().getExtendedProperties().setPages(pageCount);
            }
            if (mixedPageSizes) {
                CTSectPr firstSection = document.createParagraph()
                        .getCTP()
                        .addNewPPr()
                        .addNewSectPr();
                setWordPageSize(firstSection, 11906, 16838);
            }
            CTSectPr finalSection = document.getDocument().getBody().addNewSectPr();
            setWordPageSize(finalSection, mixedPageSizes ? 8391 : 11906, mixedPageSizes ? 11906 : 16838);
            document.write(output);
            return output.toByteArray();
        }
    }

    private void setWordPageSize(CTSectPr section, long widthTwips, long heightTwips) {
        CTPageSz pageSize = section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(widthTwips));
        pageSize.setH(BigInteger.valueOf(heightTwips));
    }

    private byte[] pngBytes(int width, int height, int dpi) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam parameters = writer.getDefaultWriteParam();
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);
        IIOMetadata metadata = writer.getDefaultImageMetadata(type, parameters);
        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
        IIOMetadataNode physical = new IIOMetadataNode("pHYs");
        int pixelsPerMeter = (int) Math.round(dpi / 0.0254);
        physical.setAttribute("pixelsPerUnitXAxis", String.valueOf(pixelsPerMeter));
        physical.setAttribute("pixelsPerUnitYAxis", String.valueOf(pixelsPerMeter));
        physical.setAttribute("unitSpecifier", "meter");
        root.appendChild(physical);
        metadata.mergeTree("javax_imageio_png_1.0", root);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            writer.write(null, new IIOImage(image, null, metadata), parameters);
            imageOutput.flush();
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private void progressToQuoted(Integer orderId) throws Exception {
        uploadOrderFile(orderId, "customer");
        mockMvc.perform(post("/api/orders/{id}/status", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"step\":\"客户已提交审核\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/{id}/workflow/quote", orderId)
                        .with(httpBasic("clerk", "demo123")))
                .andExpect(status().isOk());
        confirmQuote(orderId);
    }

    private void uploadOrderFile(Integer orderId, String username) throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "order-" + orderId + ".pdf",
                "application/pdf",
                "print-ready".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/orders/{id}/files", orderId)
                        .file(upload)
                        .with(httpBasic(username, "demo123")))
                .andExpect(status().isOk());
    }

    private void confirmQuote(Integer orderId) throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/workflow/actions/{action}", orderId, "CONFIRM_QUOTE")
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private Integer createChangeRequest(Integer orderId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders/{id}/change-requests", orderId)
                        .with(httpBasic("customer", "demo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productType": "培训手册",
                                  "colorMode": "彩色",
                                  "pageCount": 12,
                                  "copies": 2,
                                  "deliveryMode": "同城配送",
                                  "priority": "加急",
                                  "reason": "生产阶段客户要求变更规格"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private void progressToDelivery(Integer orderId) throws Exception {
        progressToProductionDone(orderId);
        mockMvc.perform(post("/api/orders/{id}/workflow/delivery-task", orderId)
                        .with(httpBasic("ops", "demo123")))
                .andExpect(status().isOk());
    }

    private void progressToProductionDone(Integer orderId) throws Exception {
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
    }

    private BigDecimal inventoryQuantity(String sku) throws Exception {
        MvcResult inventory = mockMvc.perform(get("/api/inventory-items")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andReturn();
        java.util.List<java.util.Map<String, Object>> items = JsonPath.read(inventory.getResponse().getContentAsString(), "$.data");
        return items.stream()
                .filter(item -> sku.equals(item.get("sku")))
                .findFirst()
                .map(item -> new BigDecimal(String.valueOf(item.get("quantity"))))
                .orElseThrow();
    }

    private Integer inventoryItemId(String sku) throws Exception {
        MvcResult inventory = mockMvc.perform(get("/api/inventory-items")
                        .with(httpBasic("admin", "demo123")))
                .andExpect(status().isOk())
                .andReturn();
        java.util.List<java.util.Map<String, Object>> items = JsonPath.read(inventory.getResponse().getContentAsString(), "$.data");
        return items.stream()
                .filter(item -> sku.equals(item.get("sku")))
                .findFirst()
                .map(item -> Integer.valueOf(String.valueOf(item.get("id"))))
                .orElseThrow();
    }
}
