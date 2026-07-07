package com.printshop.mis.design;

import static com.printshop.mis.shared.MisSupport.asString;
import static com.printshop.mis.shared.MisSupport.code;
import static com.printshop.mis.shared.MisSupport.forbidden;
import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.now;
import static com.printshop.mis.shared.MisSupport.number;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.DesignProject;
import com.printshop.mis.domain.DesignProjectVersion;
import com.printshop.mis.domain.DesignTemplate;
import com.printshop.mis.domain.PrintOrder;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.identity.IdentityService;
import com.printshop.mis.order.OrderService;
import com.printshop.mis.repository.DesignProjectRepository;
import com.printshop.mis.repository.DesignProjectVersionRepository;
import com.printshop.mis.repository.DesignTemplateRepository;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DesignService {

    private final IdentityService identityService;
    private final DesignTemplateRepository templates;
    private final DesignProjectRepository projects;
    private final DesignProjectVersionRepository versions;
    private final OrderService orderService;
    private final AuditTrailService audit;

    public DesignService(
            IdentityService identityService,
            DesignTemplateRepository templates,
            DesignProjectRepository projects,
            DesignProjectVersionRepository versions,
            OrderService orderService,
            AuditTrailService audit
    ) {
        this.identityService = identityService;
        this.templates = templates;
        this.projects = projects;
        this.versions = versions;
        this.orderService = orderService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DesignTemplate> listTemplates(String username) {
        UserAccount user = identityService.requireUser(username);
        if (canMaintainTemplates(user)) {
            return templates.findAll();
        }
        return templates.findByPublishedTrueOrderByUpdatedAtDesc();
    }

    public DesignTemplate saveTemplate(String username, DesignTemplate request) {
        UserAccount user = identityService.requireUser(username);
        if (!canMaintainTemplates(user)) {
            throw forbidden("只有总部运营或系统管理员可以维护模板。");
        }
        DesignTemplate template = request.id == null
                ? new DesignTemplate()
                : templates.findById(request.id).orElseThrow(() -> notFound("设计模板", request.id));
        template.templateNo = text(request.templateNo, template.templateNo == null ? code("TPL") : template.templateNo);
        template.title = text(request.title, "未命名模板");
        template.category = text(request.category, "通用");
        template.productType = text(request.productType, "宣传单页");
        template.colorMode = text(request.colorMode, "彩色");
        template.pageCount = number(request.pageCount, 1);
        template.defaultCopies = number(request.defaultCopies, 100);
        template.sizeName = text(request.sizeName, "A4");
        template.priceType = text(request.priceType, "FREE");
        template.published = request.published == null || request.published;
        template.canvasJson = text(request.canvasJson, template.canvasJson == null ? "{}" : template.canvasJson);
        template.createdBy = text(template.createdBy, user.displayName);
        template.createdAt = template.createdAt == null ? now() : template.createdAt;
        template.updatedAt = now();
        DesignTemplate saved = templates.save(template);
        audit.record(username, "DSN", "SAVE_TEMPLATE", "DESIGN_TEMPLATE", saved.id, saved.templateNo);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DesignProject> listProjects(String username) {
        UserAccount user = identityService.requireUser(username);
        return switch (user.role) {
            case "CUSTOMER" -> projects.findByCustomerIdOrderByUpdatedAtDesc(user.id);
            case "CLERK", "MANAGER" -> projects.findByStoreIdOrderByUpdatedAtDesc(user.storeId);
            case "OPS", "ADMIN" -> projects.findAllByOrderByUpdatedAtDesc();
            default -> List.of();
        };
    }

    public Map<String, Object> createProject(String username, Map<String, Object> payload) {
        UserAccount user = identityService.requireUser(username);
        if (!Set.of("CUSTOMER", "CLERK", "MANAGER", "ADMIN").contains(user.role)) {
            throw forbidden("当前角色不能创建在线设计项目。");
        }
        Long templateId = Long.valueOf(String.valueOf(payload.get("templateId")));
        DesignTemplate template = templates.findById(templateId).orElseThrow(() -> notFound("设计模板", templateId));
        if (!Boolean.TRUE.equals(template.published) && !canMaintainTemplates(user)) {
            throw notFound("设计模板", templateId);
        }
        DesignProject project = new DesignProject();
        project.projectNo = code("DSN");
        project.templateId = template.id;
        project.customerId = user.id;
        project.customerName = user.displayName;
        project.storeId = user.storeId;
        project.title = text(asString(payload.get("title")), template.title + "设计稿");
        project.status = "DRAFT";
        project.currentVersionNo = 1;
        project.canvasJson = text(asString(payload.get("canvasJson")), template.canvasJson);
        project.createdAt = now();
        project.updatedAt = now();
        DesignProject saved = projects.save(project);
        saveVersionInternal(username, saved, "初始版本", saved.canvasJson);
        audit.record(username, "DSN", "CREATE_PROJECT", "DESIGN_PROJECT", saved.id, saved.projectNo);
        return projectDetail(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProject(String username, Long id) {
        return projectDetail(requireVisibleProject(username, id));
    }

    public Map<String, Object> saveVersion(String username, Long id, Map<String, Object> payload) {
        DesignProject project = requireVisibleProject(username, id);
        String canvasJson = text(asString(payload.get("canvasJson")), project.canvasJson);
        String label = text(asString(payload.get("label")), "自动保存版本");
        DesignProjectVersion version = saveVersionInternal(username, project, label, canvasJson);
        project.canvasJson = canvasJson;
        project.currentVersionNo = version.versionNo;
        project.updatedAt = now();
        projects.save(project);
        audit.record(username, "DSN", "SAVE_PROJECT_VERSION", "DESIGN_PROJECT", project.id, "v" + version.versionNo);
        return projectDetail(project);
    }

    public Map<String, Object> restoreVersion(String username, Long id, Integer versionNo) {
        DesignProject project = requireVisibleProject(username, id);
        DesignProjectVersion version = versions.findByProjectIdAndVersionNo(project.id, versionNo)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "设计版本不存在：" + versionNo));
        project.canvasJson = version.canvasJson;
        project.currentVersionNo = version.versionNo;
        project.updatedAt = now();
        projects.save(project);
        audit.record(username, "DSN", "RESTORE_PROJECT_VERSION", "DESIGN_PROJECT", project.id, "v" + versionNo);
        return projectDetail(project);
    }

    public Map<String, Object> submitOrder(String username, Long id, Map<String, Object> payload) {
        DesignProject project = requireVisibleProject(username, id);
        if (project.submittedOrderId != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该设计项目已提交订单。");
        }
        DesignTemplate template = templates.findById(project.templateId).orElseThrow(() -> notFound("设计模板", project.templateId));
        PrintOrder request = new PrintOrder();
        request.productType = template.productType;
        request.colorMode = text(asString(payload.get("colorMode")), template.colorMode);
        request.pageCount = number(integer(payload.get("pageCount")), number(template.pageCount, 1));
        request.copies = number(integer(payload.get("copies")), number(template.defaultCopies, 1));
        request.sizeName = text(template.sizeName, "未指定尺寸");
        request.paperType = text(asString(payload.get("paperType")), "标准纸");
        request.craftType = text(asString(payload.get("craftType")), "无特殊工艺");
        request.deliveryMode = text(asString(payload.get("deliveryMode")), "到店自提");
        request.priority = text(asString(payload.get("priority")), "普通");
        PrintOrder order = orderService.createOrder(username, request);
        byte[] content = designPdfPlaceholder(project, template).getBytes(StandardCharsets.UTF_8);
        var file = orderService.attachGeneratedFile(username, order.id, project.projectNo + ".pdf", "application/pdf", content);
        project.submittedOrderId = order.id;
        project.status = "SUBMITTED";
        project.updatedAt = now();
        projects.save(project);
        audit.record(username, "DSN", "SUBMIT_DESIGN_ORDER", "ORDER", order.id, project.projectNo);
        return Map.of("project", project, "order", order, "file", file);
    }

    private DesignProjectVersion saveVersionInternal(String username, DesignProject project, String label, String canvasJson) {
        DesignProjectVersion version = new DesignProjectVersion();
        version.projectId = project.id;
        version.versionNo = Math.toIntExact(versions.countByProjectId(project.id) + 1);
        version.label = label;
        version.canvasJson = canvasJson;
        version.savedBy = identityService.requireUser(username).displayName;
        version.createdAt = now();
        return versions.save(version);
    }

    private Map<String, Object> projectDetail(DesignProject project) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("project", project);
        detail.put("template", templates.findById(project.templateId).orElse(null));
        detail.put("versions", versions.findByProjectIdOrderByVersionNoDesc(project.id));
        return detail;
    }

    private DesignProject requireVisibleProject(String username, Long id) {
        UserAccount user = identityService.requireUser(username);
        DesignProject project = projects.findById(id).orElseThrow(() -> notFound("设计项目", id));
        boolean visible = switch (user.role) {
            case "CUSTOMER" -> user.id.equals(project.customerId);
            case "CLERK", "MANAGER" -> user.storeId != null && user.storeId.equals(project.storeId);
            case "OPS", "ADMIN" -> true;
            default -> false;
        };
        if (!visible) {
            throw notFound("设计项目", id);
        }
        return project;
    }

    private boolean canMaintainTemplates(UserAccount user) {
        return Set.of("OPS", "ADMIN").contains(user.role);
    }

    private Integer integer(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String designPdfPlaceholder(DesignProject project, DesignTemplate template) {
        return """
                %%PDF-1.4
                %% PrintShop online design export placeholder
                Project: %s
                Template: %s
                Canvas: %s
                """.formatted(project.projectNo, template.title, text(project.canvasJson, "{}"));
    }
}
