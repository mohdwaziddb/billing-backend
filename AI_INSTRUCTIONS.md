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

- **Local config:** `src/main/resources/application-local.properties` (local profile). `application.properties` me sirf defaults hain.
- Default (startup) datasource `billing_common` catalog ke liye hai; **company ka data har company ke apne alag database me hai** (neeche section 3).
- **Naya tenant/company:** platform-admin se banao. Wo `billing_common.company_registry` me row + naya tenant DB (`src/main/resources/db/tenant/tenant_schema.sql` se) banata hai. Kisi shared table me `INSERT` karke company mat banao.

---

## 3) Multi-Tenant Flow (DATABASE-per-tenant)

> Pehle is file me "single DB + `company_id` Hibernate filter + `TenantContext` / `TenantFilterAspect`" likha tha. **Wo galat tha**: ye classes code me hain hi nahi, aur koi `@Filter`/`@FilterDef` nahi hai.

- **Har company = alag MySQL database.** Registry: `billing_common.company_registry` (`company_code`, `company_name`, `database_name`, `domain`, `db_host`, `status`).
- **Hibernate `multiTenancy = DATABASE`:** `src/main/java/com/billing/multitenancy/MultiTenancyJpaConfiguration.java`, `DataSourceBasedMultiTenantConnectionProviderImpl.java` (tenant ka connection pool), `CurrentTenantIdentifierResolverImpl.java` (tenant set na ho to default DB par girta hai — isliye tenant hamesha set karo).
- **Tenant kahan se aata hai:** `src/main/java/com/billing/multitenancy/RequestInterceptor.java` — `X-Company-Code` header (ya `company_code` / `companyCode` param), warna subdomain/domain. `ensureTenant(db, host)` pool lazily banata hai. Tenant id `TenantContextHolder` (ThreadLocal) me rehta hai. GET requests by default `<db>_read` pool, baaki write pool.
- **Request Flow:**
  `POST /api/v1/auth/login` → `RequestInterceptor` tenant DB chunta hai → `AuthService.login` us DB me user + BCrypt check (warna tenant super-admin, jo `companies` row par hai) → `JwtService` token: claims `authType` (USER / SUPER_ADMIN / PLATFORM_ADMIN / MFA_PENDING), `userId`, `companyCode`, `databaseName` → har request par `JwtAuthenticationFilter` token ke `databaseName` se tenant set karta hai (user lookup se PEHLE, kyunki har DB me ids 1 se shuru hote hain) → `RequestInterceptor` header/subdomain wala tenant token ke tenant se match karta hai, mismatch = **403** → `@RequiresPermission` (`RequirePermissionAspect` → `PermissionService.has`) → service → `finally` me `TenantContextHolder.clear()`.
- **Platform admin** (`/api/v1/platform-admin/**`) `billing_common` par `billingCommonJdbcTemplate` se chalta hai; kisi tenant par kaam karna ho to `PlatformAdminService.withTenantDb(...)` pattern.
- **Entities:** `company` field `@Transient` hai. Repository queries me `(:company is null or 1=1)` sirf purana leftover hai (no-op). **`company_id` filter / `@Filter` mat lagao** — isolation database level par hai.
- **Background kaam (`@Scheduled`, `ApplicationRunner`) ke paas request nahi, isliye tenant nahi.** Har ACTIVE tenant par loop karo: `companyRegistryService.listTenants(null, "ACTIVE", ...)` → `requestInterceptor.ensureTenant(db, companyRegistryService.resolveDbHostByDatabase(db))` → `TenantContextHolder.setTenantId(db)` → `@Transactional` method ko **self proxy** se call karo (naya session us DB ka) → `finally` me `clear()`. Example: `ReminderService.prepareDailyDueCustomerList()`.
- **Note:** Gym project ka `gymcommon.gym_registry` + `X-Gym-Code` flow jaisa hi pattern hai (registry + per-tenant DB + header routing), bas yahan naam `billing_common.company_registry` + `X-Company-Code` hain.

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
- **Entity:** Lombok `@Getter @Setter`, `@Table(name="invoices")` + `extends BaseEntity` (`@MappedSuperclass` audit fields). Tenant filter/`company_id` column NAHI (database-per-tenant, section 3).
- **DTO/Validation:** Controllers `Map<String,Object>` lete hain, isliye DTO ke `@NotBlank` / `@Min` / `@DecimalMin` **chalte hi nahi**. Har zaroori check (null date, qty >= 1, amount > 0, negative discount) **service me khud likho** (example: `InvoiceService.validateInvoiceRequest`, `PurchaseService.validateRequest`).
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

## 6) Deploy-Time Auto-Sync (Migration + Seed Sweep) - MANDATORY

- **Nayi Entity banayi to `db/migration/V<date>_<naam>.sql` file LIKHNA FARZ hai.** Runner SQL banata nahi, sirf chalata hai. File bhool gaye to live boot par Hibernate `validate` fail hoga (fail-loud, fail-safe).
- **File routing:** pehli lines me `-- target: common` likho to `billing_common` par (ek baar), warna har ACTIVE tenant DB par (default). Marker nahi to `billing_common.` text wali file common par jayegi.
- **Migration me sirf plain DDL/DML, `;` separated.** `DELIMITER` wali procedure/trigger file runner reject karega (WARN, apply nahi hogi) — aisi cheez haath se chalani padegi.
- **Ek file = ek statement rakho (jahan "already exists" ho sakta hai).** Runner "already exists" (table/column/key, error 1050/1060/1061) ko **poori file** ke level par tolerate karta hai: pehla aisa error aate hi file ke BAAKI statements skip ho jaate hain aur file "applied" maan li jaati hai. Isliye `ADD COLUMN` jaise statements alag-alag files me likho.
- **Purani file EDIT mat karo** (version = filename; `schema_version` table hisaab rakhti hai). Galti sudharni ho to NAYI `V<date>_` file banao.
  Exception: jo file **kabhi success se apply hui hi nahi** (har boot par FAILED retry ho rahi hai, `schema_version.success=0`) usko theek karna allowed hai — nayi file banane se order tootega. Applied (`success=1`) file ko kabhi mat chhedo.
- **Boot order:** `src/main/java/com/billing/config/TenantSeedSweepRunner.java:30` har restart par `TenantSchemaMigrationService.migrateAll()` (pehle) phir `PlatformAdminService.sweepSeedsForAllActiveTenants()` (baad me) chalata hai. Dono per-tenant try/catch me — 1 tenant fail to boot nahi rukega, WARN log aayega.
- **INACTIVE tenant sweep me SKIP hota hai.** Activate karte waqt ek baar `POST /api/v1/platform-admin/maintenance/repair-all` maarna (bulk backup endpoint, `src/main/java/com/billing/rest/PlatformAdminRestController.java`).
- **Band karna ho to:** `app.migration.enabled=false` / `app.seed-sweep.enabled=false` (default dono true).
- **Naya menu/permission** sirf `MasterSeedCatalog.MENUS` me entry maangta hai — sweep khud pahuncha dega. Permission rows bhi banengi (ADMIN/USER ko menu dikhe, isliye zaroori).
- **Additive schema change (nayi table/column) ke liye SQL file likhne ki zaroorat NAHI.** `TenantAutoDdlService` har boot par entity mappings se missing table/column khud banata hai (Hibernate update semantics: sirf CREATE, kabhi ALTER/DROP nahi). SQL file sirf inke liye likho: data backfill, rename/drop, type change, constraint surgery, procedures.
- **Deploy jar me saari files HONI chahiye (untracked/new files samet).** Live `Unknown column` 500 ka root cause yahi tha: entity field deploy ho gaya par migration runner + `V*.sql` jar me gaye hi nahi (uncommitted the). Deploy se pehle `git status` me koi `??` Java/SQL file nahi rehni chahiye.
- **Har deploy-boot ka proof log me dekho:** `[INFO] Boot migration sweep done`, `[INFO] Boot seed sweep done`, `[INFO] Boot schema verification done: drift=0`. Koi line missing ho ya `drift>0` / `[ERROR]` dikhe to usi waqt `POST /api/v1/platform-admin/maintenance/repair-all` ka `verification` section dekho — user request se pehle drift pakda jayega.
- **`TenantSchemaVerifier` (boot + repair-all) entity-metamodel ko INFORMATION_SCHEMA se milata hai** (tenant DBs me `platform_settings` chhodkar, wo `billing_common` me check hota hai). Ye read-only hai — kuch banata/bigadta nahi, sirf `DRIFT` chillata hai.

---

## 7) UI Performance Rules (Frontend bhi - Har page - MANDATORY)

Ye rules isi AI instruction file me likhi hai (yahi ek "AI Instructions" file hai) — **frontend UI code pe bhi 100% applicable hain**. Kisi bhi page pe inka ullanghan = code review fail.

- **Animated / lagatar-moving element pe `backdrop-blur` (`backdrop-filter`) KABHI NAHI.** Browser har animation frame me poora backdrop dobara blur karta hai → GPU/CPU 100% → low-end Windows machines pe **poora PC hang**. Case study: login page ke floating badges (`billing-frontend/src/components/login/AnalyticsShowcase.tsx`) — `backdrop-blur` + `.login-float` infinite animation = system hang bug tha. Solid `bg-white/95` ya halka solid background use karo, blur nahi.
- **Bade decorative elements pe `filter: blur(...)` (`blur-xl/2xl/3xl`) mat lagao.** Low-end GPU pe paint spike + Chromium me GPU memory leak (page baar-baar kholne par). Iski jagah bina-filter `radial-gradient` circles use karo — dikhne me same soft glow, cost ~zero:
  ```jsx
  // Galat:
  <div className="h-80 w-80 rounded-full bg-[#dbe7ff]/80 blur-3xl" />
  // Sahi:
  <div className="h-96 w-96 rounded-full bg-[radial-gradient(circle,#dbe7ff_0%,rgba(219,231,255,0.55)_45%,rgba(219,231,255,0)_72%)]" />
  ```
- **Infinite animations sirf compositor-friendly properties pe** (`transform`, `opacity`). Kisi bhi infinite animation ke liye: `@media (prefers-reduced-motion: reduce)` me animation off karo, aur `will-change` sirf usi property pe lagao jo actually animate ho rahi hai. Layout-affecting properties (width/height/top/left/box-shadow/filter) ko infinite animation me kabhi mat animate karo.
- **Login page hamesha GPU-light rakho.** Ye sabse pehla page hai aur weakest client machine pe bhi chalna chahiye. Login page pe koi naya visual effect add karne se pehle: Chrome DevTools → Performance (10s record) ya `Shift+Esc` Task Manager me GPU/CPU spike check karo — spike dikhe to effect simplify karo.
- **Redirect loops kabhi nahi:** `window.location.href = ...` set karne se pehle current path/target compare karo (guarded redirect). `setInterval`/`setTimeout` hamesha React effect cleanup me clear karo. Koi bhi auto-redirect aisa na ho jo wapas usi page pe loop kar jaye.
- **Naya visual effect / animation add karte waqt checklist:** (1) koi `backdrop-filter` animated element pe nahi, (2) koi bade element pe `filter: blur` nahi, (3) infinite animation transform/opacity-only + reduced-motion me off, (4) login page pe extra GPU cost zero, (5) koi naya timer/redirect bina cleanup nahi.

---

**Is file ko har AI task se pehle padhna ZAROORI hai. Agar iska ullanghan hua to code review fail samjho.**
