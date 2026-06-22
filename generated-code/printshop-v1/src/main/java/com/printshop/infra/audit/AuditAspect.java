package com.printshop.infra.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 将 @Auditable 服务调用转换为审计快照。
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditRecorder auditRecorder;

    public AuditAspect(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @Around("@annotation(auditable)")
    public Object recordAudit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();
        auditRecorder.record(auditable.action(), joinPoint.getSignature().toShortString() + " => " + result);
        return result;
    }
}
