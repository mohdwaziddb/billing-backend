package com.billing.tenant;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantFilterAspect {

    private final EntityManager entityManager;

    @Around("@within(org.springframework.stereotype.Service) || @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object enableTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!TenantContext.hasTenant()) {
            return joinPoint.proceed();
        }

        Session session = entityManager.unwrap(Session.class);
        boolean newlyEnabled = session.getEnabledFilter("tenantFilter") == null;
        if (newlyEnabled) {
            Filter filter = session.enableFilter("tenantFilter");
            filter.setParameter("companyId", TenantContext.getCompanyId());
        }

        try {
            return joinPoint.proceed();
        } finally {
            if (newlyEnabled) {
                session.disableFilter("tenantFilter");
            }
        }
    }
}
