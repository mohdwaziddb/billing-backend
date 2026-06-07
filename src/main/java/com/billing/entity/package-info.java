@org.hibernate.annotations.FilterDef(
        name = "tenantFilter",
        parameters = @org.hibernate.annotations.ParamDef(name = "companyId", type = Long.class)
)
package com.billing.entity;
