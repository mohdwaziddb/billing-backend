# AI INSTRUCTIONS - Backend Project (Portable)

**Har AI modal is file ko pehle padhe, tabhi kaam start kare. Yeh project ka mandatory coding guide hai.**
> **Note:** Is file me diye gaye saare paths **project root se relative** hain. Chahe project kisi bhi drive/folder me ho, hamesha relative path `src/...` use karo. Absolute path hardcode mat karo.

---

## 1) DataTypeUtility ka use - MANDATORY

- **Jab bhi `long`, `String`, `Integer`, `Boolean`, `Float`, `Double` ka conversion karna ho, direct `String.valueOf()` / `Long.parseLong()` / `obj.toString()` MAT karo.**
- **Hamesha `DataTypeUtility` ka use karo:**

```java
import static com.billing.util.DataTypeUtility.*;
// ya
import com.billing.util.DataTypeUtility;

// Sahi:
String name = DataTypeUtility.stringValue(param.get("customer_name"));
Long id = DataTypeUtility.longValue(param.get("id"));
Long fk = DataTypeUtility.getForeignKeyValue(param.get("company_id"));
Integer limit = DataTypeUtility.integerValue(param.get("limit"));
boolean active = DataTypeUtility.booleanValue(param.get("is_active"));
long zero = DataTypeUtility.longZeroValue(obj);
String safe = DataTypeUtility.stringNullValue(obj);
String indian = DataTypeUtility.getIndianDateFormat(usDate);
String us = DataTypeUtility.getUSDateFormat(indianDate);
Date d = DataTypeUtility.getDateObjectFromIndianFormat(dateStr);

// Galat (mana hai):
String name = (String) param.get("customer_name");
Long id = Long.parseLong(param.get("id").toString());
```

- Service me `import static com.billing.util.DataTypeUtility.*;` karke `stringValue()`, `longValue()` direct bhi call kar sakte ho, par import hamesha `DataTypeUtility` se hona chahiye.
- **Utility Location (relative):** `src/main/java/com/billing/util/DataTypeUtility.java:15` - yahi central utility hai, isme proper `null` / `"null"` / `"undefined"` / `"all"` / `""` / `<=0` checks already hain.

---

## 2) Local DB kaha se uthega

- **Local DB `billing` ka config `application.properties` se nahi uthega.**
- **Sahi jagah (relative path):** `src/main/resources/application-local.properties:2`
  ```properties
  # File: src/main/resources/application-local.properties
  spring.datasource.url=jdbc:mysql://localhost:3306/billing?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Kolkata
  spring.datasource.username=root
  spring.datasource.password=hrhk
  spring.jpa.hibernate.ddl-auto=update
  ```
  > Project root se relative: `src/main/resources/...` . Kisi bhi OS me absolute path hardcode mat karo.
- `src/main/resources/application.properties` me `spring.profiles.active` sirf defaults ke liye hai. Runtime me `src/main/resources/application-local.properties` override karta hai (local profile).
- Naya tenant/company banana ho (e.g. `company_id=2`) to yaha DB change nahi, `billing.company` table me `INSERT` karo, `company_id` se tenant isolation `src/main/java/com/billing/tenant/TenantContext.java:5` + `TenantFilterAspect.java:18` via `company_id` filter handle karega.

---

## 3) Multi-Tenant Flow (company_id Filter - Single DB)

- `billing` = single DB (company, customer, invoice, product etc) — `CREATE DATABASE billing` — `company_id` column se isolated
- **Tenant Mechanism:** Hibernate `@FilterDef tenantFilter` — `src/main/java/com/billing/entity/package-info.java` + `src/main/java/com/billing/tenant/TenantContext.java:5` (ThreadLocal<Long> CURRENT_COMPANY_ID) + `src/main/java/com/billing/tenant/TenantFilterAspect.java:18` (`@Around @Service || @Transactional`)

- **Request Flow:**
  `POST /api/v1/auth/login` → `src/main/java/com/billing/controller/AuthController.java` → `src/main/java/com/billing/service/AuthService.java` validates `company` + `user` → JWT create `JwtService.java:47` `subject=userId` + `claim:companyId` → `src/main/java/com/billing/security/JwtAuthenticationFilter.java` extracts `companyId` → `TenantContext.setCompanyId(companyId)` → `TenantFilterAspect.java:22` `session.enableFilter("tenantFilter").setParameter("companyId", ...)` → All `InvoiceRepository`, `CustomerRepository` etc queries auto-filtered by `company_id = :companyId` → response → `TenantContext.clear()` in filter `finally`.

- **Note:** Gym project ka `gymcommon.gym_registry` + `dbgym` dual-DB + `X-Gym-Code` + `MysqlDataSourceService.java:23` + `MultiTenancyJpaConfiguration:92` flow yaha **applicable nahi** hai. Yaha single DB + `company_id` filter hai.

---

## 4) Code Style (Gym Project Jaisa)

- **Service:** `Map<String,Object> param, HttpServletRequest request` signature → `try/catch`, `DataTypeUtility` conversions, `@Transactional`, `ResponseEntity<GeneralResponse>` (Gym me `MobileResponseDTOFactory.successMessage/failedMessage` tha, yaha equivalent `GeneralResponse` + `ResponseEntity` use karo).
  ```java
  // src/main/java/com/billing/service/InvoiceService.java
  @Transactional
  public ResponseEntity<?> createInvoice(Map<String,Object> param, HttpServletRequest request) {
      try {
          Long customerId = DataTypeUtility.getForeignKeyValue(param.get("customer_id"));
          String invoiceDate = DataTypeUtility.stringValue(param.get("invoice_date"));
          if (customerId == null) {
              return ResponseEntity.badRequest().body(new GeneralResponse<>(false, "customer_id required", null));
          }
          // ... business logic with DataTypeUtility conversions
          return ResponseEntity.ok(new GeneralResponse<>(true, "Success", data));
      } catch (Exception e) {
          return ResponseEntity.internalServerError().body(new GeneralResponse<>(false, e.getMessage(), null));
      }
  }
  ```
- **Brace Style - MANDATORY:** Chahe single line `if` ho, hamesha `{}` me likho:
  ```java
  // Sahi:
  if (startDate == null) {
      startDate = DataTypeUtility.parseLocalDate(DataTypeUtility.stringValue(param.get("startDate")), "dd-MM-yyyy");
  }
  // Galat (mana hai):
  if (startDate == null) startDate = DataTypeUtility.parseLocalDate(DataTypeUtility.stringValue(param.get("startDate")), "dd-MM-yyyy");
  ```
- **Variable Naming - MANDATORY:** Variable name hamesha relatable (meaningful, context se juda) rakho:
  ```java
  // Sahi:
  Long customerId = DataTypeUtility.getForeignKeyValue(param.get("customerId"));
  String invoiceDate = DataTypeUtility.stringValue(param.get("invoiceDate"));
  BigDecimal totalAmount = DataTypeUtility.bigDecimalObjectValue(param.get("totalAmount"));
  // Galat (mana hai):
  Long x = DataTypeUtility.longValue(param.get("customerId"));
  String s = DataTypeUtility.stringValue(param.get("invoiceDate"));
  Object temp = param.get("totalAmount");
  ```
- **Controller:** `param` ko `DataTypeUtility` se validate karo, `@RequiresPermission("invoice:create")` + `SecurityConfig.java` RBAC check. Gym me `SanitizeData.sanitizeMapObj(param)` tha, yaha `DataTypeUtility.stringValue/longValue` se santization hota hai.
- **Entity:** Lombok `@Getter @Setter`, `@Table(name="invoices")` + `@Filter(name="tenantFilter", condition="company_id = :companyId")` + `extends BaseEntity` (`@MappedSuperclass` audit fields).
- **DTO/Validation:** `src/main/java/com/billing/dto/...` me BeanValidation (`@NotBlank`, `@Min`, `@DecimalMin`) + service me `DataTypeUtility` double-check.
- **Controller-Service Separation - MANDATORY:** Sara kaam controller layer par mat karo, service layer ka use karo. Controller sirf `param` sanitization, `DataTypeUtility` se basic validation, permission check aur service call kare; business logic, DB access, calculations hamesha `src/main/java/com/billing/service/...` me rakho:
  ```java
  // Sahi (Controller - thin):
  @GetMapping
  public ResponseEntity<?> list(@RequestParam Map<String,Object> param, Authentication auth, HttpServletRequest req) {
      try {
          param = SanitizeData.sanitizeMapObj(param);
          String search = DataTypeUtility.stringValue(param.get("search"));
          if (search.length() == 0) {
              search = null;
          }
          return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", expenseService.page(auth.getName(), search, expenseType, categoryId, customerId, invoiceId, startDate, endDate, createdByRole, page, size)), HttpStatus.OK);
      } catch (Exception e) {
          return mobileResponseDTOFactory.reportInternalServerError(e);
      }
  }
  // Sahi (Service - business logic):
  // src/main/java/com/billing/service/ExpenseService.java:57
  // page(), create(), update() me DB query, calculations, audit log

  // Galat (mana hai - controller me business logic):
  @GetMapping
  public ResponseEntity<?> list(@RequestParam Map<String,Object> param, Authentication auth) {
      // Controller me hi repository ko call karna, amount calculate karna, GST nikalna mana hai
      List<Expense> list = expenseRepository.findAll(); // Galat - ye service me hona chahiye
      BigDecimal total = list.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add); // Galat
      return ResponseEntity.ok(list);
  }
  ```

---

## 5) Project Positioning (Bizio) - Moved from AGENTS.md

- **Bizio is a business growth platform for SMBs in India, NOT just billing/invoicing software.** It helps businesses grow via billing, invoicing, customers, inventory, payments, GST/taxes, reports and insights. Keep this positioning in mind whenever talking about Bizio or writing Bizio-facing content (including AI prompts).

---

**Is file ko har AI task se pehle padhna ZAROORI hai. Agar iska ullanghan hua to code review fail samjho.**
