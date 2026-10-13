package com.billing.service;

import com.billing.core.Registry;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Platform DB Dump Manager (RDS-style, EC2 MySQL): queued mysqldump jobs.
 *
 * Everything lives in billing_common.dump_requests (platform-level, like
 * company_registry): no tenant switch, no JPA pools involved — the lazy
 * tenant map is untouched. Dumps stream straight to disk (never web root,
 * never whole-file in memory). Passwords never touch CLI args or logs.
 */
@Service
public class DatabaseDumpService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseDumpService.class);

    private static final Set<String> SYSTEM_DATABASES = Set.of("mysql", "sys", "information_schema", "performance_schema");
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_]{1,64}$");
    private static final Pattern DATE_ONLY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Set<String> MODES = Set.of("FULL", "INCLUDE", "EXCLUDE");
    private static final Set<String> STATUSES = Set.of("QUEUED", "RUNNING", "COMPLETED", "FAILED", "CANCELLED");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int HISTORY_LIMIT = 100;
    private static final long STDERR_CAP = 64 * 1024;

    @Autowired
    @Qualifier("billingCommonJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompanyRegistryService companyRegistryService;

    @Value("${app.dump.dir:}")
    private String dumpDirProp;

    @Value("${app.dump.retention-days:7}")
    private int retentionDays;

    @Value("${app.dump.timeout-minutes:30}")
    private long timeoutMinutes;

    private volatile boolean reclaimed;

    // Running dumps for Stop support: id -> live process (binary path).
    private final java.util.concurrent.ConcurrentHashMap<Long, Process> runningProcesses =
            new java.util.concurrent.ConcurrentHashMap<>();
    // Cancel requests: id -> requested-by. Both paths check this and stop.
    private final java.util.concurrent.ConcurrentHashMap<Long, String> cancelRequested =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final class DumpCancelled extends RuntimeException {
        DumpCancelled() {
            super("cancelled");
        }
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(resolveDumpDir());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create dump directory: " + e.getMessage(), e);
        }
        // Self-heal: no Flyway wired, migration files run by hand — fresh
        // environments must not 500 until someone runs the SQL. CREATE TABLE
        // IF NOT EXISTS is a no-op when the migration already ran.
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS billing_common.dump_requests ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "company_code VARCHAR(50) NULL,"
                    + "database_name VARCHAR(100) NOT NULL,"
                    + "db_host VARCHAR(100) NOT NULL DEFAULT 'localhost:3306',"
                    + "mode VARCHAR(20) NOT NULL DEFAULT 'FULL',"
                    + "tables TEXT NULL,"
                    + "status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',"
                    + "requested_by VARCHAR(255) NULL,"
                    + "file_path VARCHAR(500) NULL,"
                    + "file_size BIGINT NULL DEFAULT 0,"
                    + "error TEXT NULL,"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "started_at DATETIME NULL,"
                    + "finished_at DATETIME NULL,"
                    + "INDEX idx_dump_status_created (status, created_at),"
                    + "INDEX idx_dump_db_status (database_name, status)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        } catch (Exception e) {
            log.warn("Dump table self-heal failed (run the migration SQL by hand): {}", e.getMessage());
        }
    }

    // ---------- read APIs (controller) ----------

    public List<String> mysqlInstances() {
        List<String> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT db_host FROM billing_common.company_registry WHERE db_host IS NOT NULL AND db_host <> ''");
            for (Map<String, Object> row : rows) {
                String host = str(row.get("db_host")).trim().replace("jdbc:mysql://", "").replace("/", "");
                if (!host.isEmpty() && !out.contains(host)) {
                    out.add(host);
                }
            }
        } catch (Exception e) {
            log.warn("Dump instances lookup failed: {}", e.getMessage());
        }
        String localDefault = "localhost:3306";
        if (!out.contains(localDefault)) {
            out.add(0, localDefault);
        }
        return out;
    }

    public List<Map<String, Object>> knownDatabases() {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> common = new LinkedHashMap<>();
        common.put("companyCode", null);
        common.put("databaseName", "billing_common");
        common.put("dbHost", defaultHost());
        common.put("status", "CATALOG");
        out.add(common);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT company_code, database_name, db_host, status FROM billing_common.company_registry ORDER BY database_name");
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("companyCode", str(row.get("company_code")));
                item.put("databaseName", str(row.get("database_name")));
                item.put("dbHost", str(row.get("db_host")));
                item.put("status", str(row.get("status")));
                out.add(item);
            }
        } catch (Exception e) {
            log.warn("Dump database list lookup failed: {}", e.getMessage());
        }
        return out;
    }

    public Map<String, Object> validateDatabase(String database, List<String> tables) {
        String db = cleanName(database);
        if (db == null) {
            throw new BadRequestException("Database name is required (letters, digits, underscore).");
        }
        boolean isCommon = "billing_common".equalsIgnoreCase(db);
        if (!isCommon && !companyRegistryService.isValidDatabase(db)) {
            throw new BadRequestException("Unknown database '" + db + "'. Only registered tenant databases and billing_common can be dumped.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("database", db);
        result.put("isCommon", isCommon);
        result.put("host", resolveHost(db));
        Boolean exists = schemaExists(db);
        result.put("databaseExists", exists);
        if (exists != null && !exists) {
            throw new BadRequestException("Database '" + db + "' does not exist on this server.");
        }
        List<String> cleanTables = cleanTableList(tables);
        List<String> missing = new ArrayList<>();
        if (!cleanTables.isEmpty() && exists != null && exists) {
            for (String table : cleanTables) {
                if (!tableExists(db, table)) {
                    missing.add(table);
                }
            }
        }
        result.put("missingTables", missing);
        result.put("tablesChecked", exists != null && exists);
        if (!missing.isEmpty()) {
            throw new BadRequestException("Tables not found in '" + db + "': " + String.join(", ", missing));
        }
        result.put("valid", true);
        return result;
    }

    public Map<String, Object> queueDump(String database, String mode, List<String> tables, String requestedBy) {
        String db = cleanName(database);
        if (db == null) {
            throw new BadRequestException("Database name is required (letters, digits, underscore).");
        }
        String normalizedMode = mode == null ? "FULL" : mode.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(normalizedMode)) {
            throw new BadRequestException("Dump mode must be FULL, INCLUDE or EXCLUDE.");
        }
        List<String> cleanTables = cleanTableList(tables);
        if (!"FULL".equals(normalizedMode) && cleanTables.isEmpty()) {
            throw new BadRequestException("Table list is required for INCLUDE/EXCLUDE mode (comma-separated).");
        }
        // Full server-side revalidation: never trust the Validate-then-Queue gap.
        validateDatabase(db, "FULL".equals(normalizedMode) ? List.of() : cleanTables);
        Integer busy = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM billing_common.dump_requests WHERE database_name=? AND status IN ('QUEUED','RUNNING')",
                Integer.class, db);
        if (busy != null && busy > 0) {
            throw new BadRequestException("A dump for '" + db + "' is already queued or running.");
        }
        String code = "billing_common".equalsIgnoreCase(db) ? null : companyCodeFor(db);
        String host = resolveHost(db);
        jdbcTemplate.update(
                "INSERT INTO billing_common.dump_requests (company_code, database_name, db_host, mode, tables, status, requested_by)"
                        + " VALUES (?,?,?,?,?,'QUEUED',?)",
                code, db, host, normalizedMode,
                cleanTables.isEmpty() ? null : String.join(",", cleanTables),
                requestedBy == null ? "" : requestedBy.trim());
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        log.info("Dump queued: id={} database={} mode={} by={}", id, db, normalizedMode, requestedBy);
        return dumpRow(jdbcTemplate.queryForMap(
                "SELECT * FROM billing_common.dump_requests WHERE id=?", id));
    }

    public Map<String, Object> history(String date, String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (date != null && DATE_ONLY.matcher(date.trim()).matches()) {
            where.append(" AND DATE(created_at)=?");
            args.add(date.trim());
        }
        if (status != null && STATUSES.contains(status.trim().toUpperCase(Locale.ROOT))) {
            where.append(" AND status=?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM billing_common.dump_requests" + where, Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(safePage * safeSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM billing_common.dump_requests" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            records.add(dumpRow(row));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("total", total == null ? 0 : total);
        out.put("page", safePage);
        out.put("size", safeSize);
        return out;
    }

    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalDumps", count("SELECT COUNT(*) FROM billing_common.dump_requests WHERE DATE(created_at)=CURDATE()"));
        out.put("completed", count("SELECT COUNT(*) FROM billing_common.dump_requests WHERE status='COMPLETED' AND DATE(created_at)=CURDATE()"));
        out.put("failed", count("SELECT COUNT(*) FROM billing_common.dump_requests WHERE status='FAILED' AND DATE(created_at)=CURDATE()"));
        out.put("inProgress", count("SELECT COUNT(*) FROM billing_common.dump_requests WHERE status IN ('QUEUED','RUNNING')"));
        out.put("activeUsers", count("SELECT COUNT(DISTINCT requested_by) FROM billing_common.dump_requests WHERE DATE(created_at)=CURDATE() AND requested_by <> ''"));
        List<Map<String, Object>> perUser = jdbcTemplate.queryForList(
                "SELECT requested_by AS username, COUNT(*) AS dumps FROM billing_common.dump_requests"
                        + " WHERE DATE(created_at)=CURDATE() GROUP BY requested_by ORDER BY dumps DESC LIMIT 10");
        List<Map<String, Object>> users = new ArrayList<>();
        for (Map<String, Object> row : perUser) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("username", str(row.get("username")));
            item.put("dumps", num(row.get("dumps")));
            users.add(item);
        }
        out.put("perUser", users);
        return out;
    }

    /** Returns the dump file for a COMPLETED row, or throws 404 (missing/expired/failed). */
    public Path dumpFile(long id) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap("SELECT * FROM billing_common.dump_requests WHERE id=?", id);
        } catch (Exception e) {
            throw new ResourceNotFoundException("Dump request not found");
        }
        if (!"COMPLETED".equalsIgnoreCase(str(row.get("status")))) {
            throw new BadRequestException("Dump #" + id + " is " + str(row.get("status")) + " — download available after completion.");
        }
        String path = str(row.get("file_path"));
        if (path == null || path.isBlank()) {
            throw new ResourceNotFoundException("Dump file expired and was cleaned up. Queue a fresh dump.");
        }
        Path file = Paths.get(path);
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new ResourceNotFoundException("Dump file expired and was cleaned up. Queue a fresh dump.");
        }
        return file;
    }

    public String dumpFileName(long id) {
        String db = str(jdbcTemplate.queryForObject(
                "SELECT database_name FROM billing_common.dump_requests WHERE id=?", String.class, id));
        if (db == null || db.isBlank()) {
            db = "database";
        }
        return db + "_dump_" + id + ".sql";
    }

    // ---------- worker ----------

    @Scheduled(fixedDelayString = "${app.dump.poll-ms:30000}")
    public void processQueue() {
        if (!reclaimed) {
            reclaimed = true;
            try {
                int back = jdbcTemplate.update(
                        "UPDATE billing_common.dump_requests SET status='QUEUED', started_at=NULL WHERE status='RUNNING'");
                if (back > 0) {
                    log.info("Dump worker: reclaimed {} stale RUNNING rows after restart", back);
                }
            } catch (Exception e) {
                log.warn("Dump worker reclaim failed (table missing? run the migration): {}", e.getMessage());
                return;
            }
        }
        Long id = null;
        try {
            List<Long> queued = jdbcTemplate.queryForList(
                    "SELECT id FROM billing_common.dump_requests WHERE status='QUEUED' ORDER BY id LIMIT 1", Long.class);
            if (queued.isEmpty()) {
                return;
            }
            id = queued.get(0);
            int claimed = jdbcTemplate.update(
                    "UPDATE billing_common.dump_requests SET status='RUNNING', started_at=NOW() WHERE id=? AND status='QUEUED'", id);
            if (claimed != 1) {
                return;
            }
        } catch (Exception e) {
            log.warn("Dump worker claim failed: {}", e.getMessage());
            return;
        }
        runDump(id);
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Kolkata")
    public void cleanupExpired() {
        int days = retentionDays <= 0 ? 7 : retentionDays;
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, file_path FROM billing_common.dump_requests WHERE file_path IS NOT NULL AND created_at < DATE_SUB(NOW(), INTERVAL ? DAY)",
                    days);
        } catch (Exception e) {
            log.warn("Dump cleanup lookup failed: {}", e.getMessage());
            return;
        }
        int removed = 0;
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            String path = str(row.get("file_path"));
            try {
                if (path != null && !path.isBlank()) {
                    Files.deleteIfExists(Paths.get(path));
                }
                jdbcTemplate.update("UPDATE billing_common.dump_requests SET file_path=NULL WHERE id=?", id);
                removed++;
            } catch (Exception e) {
                log.warn("Dump cleanup failed for id {}: {}", id, e.getMessage());
            }
        }
        if (removed > 0) {
            log.info("Dump cleanup: expired {} files older than {} days", removed, days);
        }
    }

    // ---------- internals ----------

    private void runDump(long id) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM billing_common.dump_requests WHERE id=?", id);
        String db = str(row.get("database_name"));
        String mode = str(row.get("mode"));
        String tablesCsv = str(row.get("tables"));
        String requestedBy = str(row.get("requested_by"));
        log.info("Dump started: id={} database={} mode={} by={}", id, db, mode, requestedBy);
        Path outFile = null;
        try {
            outFile = resolveDumpDir().resolve(
                    db + "_" + LocalDateTime.now().format(FILE_STAMP) + "_" + UUID.randomUUID().toString().substring(0, 6) + ".sql");
            if (mysqldumpAvailable()) {
                runMysqldump(db, mode, splitCsv(tablesCsv), outFile, id);
            } else {
                // Windows dev (no binary): same .sql via pure JDBC, no binary needed.
                runJdbcDump(db, mode, splitCsv(tablesCsv), outFile, id);
            }
            long size = Files.size(outFile);
            jdbcTemplate.update(
                    "UPDATE billing_common.dump_requests SET status='COMPLETED', file_path=?, file_size=?, finished_at=NOW(), error=NULL WHERE id=?",
                    outFile.toString(), size, id);
            log.info("Dump completed: id={} database={} size={} bytes", id, db, size);
        } catch (Exception e) {
            String cancelledBy = cancelRequested.remove(id);
            boolean cancelled = cancelledBy != null || e instanceof DumpCancelled;
            String message = e.getMessage() == null ? "dump failed" : e.getMessage();
            if (message.length() > 2000) {
                message = message.substring(0, 2000);
            }
            try {
                if (outFile != null) {
                    Files.deleteIfExists(outFile);
                }
                if (cancelled) {
                    jdbcTemplate.update(
                            "UPDATE billing_common.dump_requests SET status='CANCELLED', finished_at=NOW(), error=? WHERE id=?",
                            "Cancelled" + (cancelledBy == null || cancelledBy.isBlank() ? "" : " by " + cancelledBy), id);
                } else {
                    jdbcTemplate.update(
                            "UPDATE billing_common.dump_requests SET status='FAILED', finished_at=NOW(), error=? WHERE id=?",
                            message, id);
                }
            } catch (Exception ignored) {
            }
            if (cancelled) {
                log.info("Dump cancelled: id={} database={}", id, db);
            } else {
                log.warn("Dump failed: id={} database={}: {}", id, db, message);
            }
        } finally {
            runningProcesses.remove(id);
            cancelRequested.remove(id);
        }
    }

    private volatile Boolean mysqldumpPresent;

    private boolean mysqldumpAvailable() {
        Boolean cached = mysqldumpPresent;
        if (cached != null) {
            return cached;
        }
        boolean found = false;
        try {
            Process probe = new ProcessBuilder("mysqldump", "--version").redirectErrorStream(true).start();
            found = probe.waitFor(10, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (Exception ignored) {
        }
        mysqldumpPresent = found;
        log.info("mysqldump binary {} — dumps will use {}",
                found ? "found on this host" : "NOT found on this host",
                found ? "mysqldump" : "pure-JDBC fallback");
        return found;
    }

    /**
     * Pure-JDBC dump (Windows dev and any host without the mysqldump binary).
     * Same .sql shape: consistent snapshot (InnoDB), DROP+CREATE per table,
     * batched multi-row INSERTs, FK checks off for restore, triggers, and
     * (FULL only) routines. Slower than the binary on GB-scale databases,
     * exact for our MB-scale tenants.
     */
    private void runJdbcDump(String db, String mode, List<String> tables, Path outFile, long dumpId) throws Exception {
        String hostPort = resolveHost(db);
        String username = Registry.dbmap.get("username");
        String password = Registry.dbmap.get("password");
        if (username == null || username.isBlank()) {
            username = "root";
        }
        if (password == null) {
            throw new BadRequestException("Database credentials are not configured for dumps.");
        }
        List<String> targetTables = "FULL".equalsIgnoreCase(mode) ? List.of() : new ArrayList<>(tables);
        String jdbcUrl = "jdbc:mysql://" + hostPort + "/" + db
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata&useUnicode=true&characterEncoding=utf-8";
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, username, password);
                java.io.BufferedWriter out = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            // REPEATABLE READ (MySQL default) + autoCommit(false): pehla SELECT hi
            // consistent snapshot kholta hai — mysqldump --single-transaction jaisa.
            conn.setAutoCommit(false);
            out.write("-- Billing Dump Manager (pure-JDBC fallback)\n");
            out.write("-- database: `" + db + "`  mode: " + mode.toUpperCase(Locale.ROOT) + "\n");
            out.write("-- generated: " + LocalDateTime.now() + "\n\n");
            out.write("/*!40101 SET NAMES utf8mb4 */;\nSET FOREIGN_KEY_CHECKS=0;\n\n");
            List<String> allTables = listBaseTables(conn, db);
            List<String> wanted = targetTables.isEmpty() ? allTables : targetTables;
            if (!"FULL".equalsIgnoreCase(mode) && "EXCLUDE".equalsIgnoreCase(mode)) {
                List<String> kept = new ArrayList<>(allTables);
                kept.removeAll(targetTables);
                wanted = kept;
            }
            for (String table : wanted) {
                if (cancelRequested.containsKey(dumpId)) {
                    throw new DumpCancelled();
                }
                dumpJdbcTable(conn, db, table, out, dumpId);
            }
            out.write("\nSET FOREIGN_KEY_CHECKS=1;\n");
            dumpJdbcTriggers(conn, db, out);
            if ("FULL".equalsIgnoreCase(mode)) {
                dumpJdbcRoutines(conn, db, out);
            }
            out.write("\n-- Dump completed: `" + db + "`\n");
            out.flush();
            conn.commit();
        }
        if (Files.size(outFile) == 0) {
            throw new BadRequestException("JDBC dump produced an empty file.");
        }
        log.info("Dump completed via pure-JDBC fallback: database={} file={}", db, outFile.getFileName());
    }

    private static List<String> listBaseTables(java.sql.Connection conn, String db) throws Exception {
        List<String> out = new ArrayList<>();
        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=? AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME")) {
            ps.setString(1, db);
            ps.setQueryTimeout(300);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    private void dumpJdbcTable(java.sql.Connection conn, String db, String table, java.io.BufferedWriter out, long dumpId) throws Exception {
        String quoted = "`" + db + "`.`" + table + "`";
        String create;
        try (java.sql.Statement st = conn.createStatement();
                java.sql.ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + quoted)) {
            if (!rs.next()) {
                return;
            }
            create = rs.getString(2);
        }
        out.write("\n-- Table structure for `" + table + "`\nDROP TABLE IF EXISTS `" + table + "`;\n" + create + ";\n");
        try (java.sql.Statement st = conn.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY)) {
            st.setFetchSize(Integer.MIN_VALUE);
            st.setQueryTimeout(25 * 60);
            try (java.sql.ResultSet rs = st.executeQuery("SELECT * FROM " + quoted)) {
                java.sql.ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                StringBuilder colList = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) {
                        colList.append(",");
                    }
                    colList.append("`").append(meta.getColumnName(i).replace("`", "")).append("`");
                }
                out.write("\n-- Data for `" + table + "`\n");
                StringBuilder batch = new StringBuilder();
                int inBatch = 0;
                while (rs.next()) {
                    if (inBatch == 0) {
                        batch.append("INSERT INTO `").append(table).append("` (").append(colList).append(") VALUES ");
                    } else {
                        batch.append(",");
                    }
                    batch.append("(");
                    for (int i = 1; i <= cols; i++) {
                        if (i > 1) {
                            batch.append(",");
                        }
                        batch.append(jdbcLiteral(rs, meta, i));
                    }
                    batch.append(")");
                    inBatch++;
                    if (inBatch >= 200) {
                        batch.append(";\n");
                        out.write(batch.toString());
                        batch.setLength(0);
                        inBatch = 0;
                        if (cancelRequested.containsKey(dumpId)) {
                            throw new DumpCancelled();
                        }
                    }
                }
                if (inBatch > 0) {
                    batch.append(";\n");
                    out.write(batch.toString());
                }
            }
        }
    }

    private static String jdbcLiteral(java.sql.ResultSet rs, java.sql.ResultSetMetaData meta, int col) throws Exception {
        Object value = rs.getObject(col);
        if (value == null || rs.wasNull()) {
            return "NULL";
        }
        int type = meta.getColumnType(col);
        switch (type) {
            case java.sql.Types.BIT:
            case java.sql.Types.BOOLEAN:
                return (value instanceof Boolean b) ? (b ? "1" : "0") : value.toString();
            case java.sql.Types.TINYINT:
            case java.sql.Types.SMALLINT:
            case java.sql.Types.INTEGER:
            case java.sql.Types.BIGINT:
            case java.sql.Types.DECIMAL:
            case java.sql.Types.NUMERIC:
            case java.sql.Types.FLOAT:
            case java.sql.Types.REAL:
            case java.sql.Types.DOUBLE:
                return value.toString();
            case java.sql.Types.BINARY:
            case java.sql.Types.VARBINARY:
            case java.sql.Types.LONGVARBINARY:
            case java.sql.Types.BLOB:
            case java.sql.Types.CLOB:
                if (value instanceof byte[] bytes) {
                    StringBuilder hex = new StringBuilder("0x");
                    for (byte single : bytes) {
                        hex.append(String.format("%02X", single & 0xFF));
                    }
                    return hex.toString();
                }
                return "'" + escapeSql(value.toString()) + "'";
            default:
                return "'" + escapeSql(value.toString()) + "'";
        }
    }

    private static String escapeSql(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\': out.append("\\\\"); break;
                case '\'': out.append("\\'"); break;
                case '\0': out.append("\\0"); break;
                case '\b': out.append("\\b"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '\032': out.append("\\Z"); break;
                default: out.append(ch); break;
            }
        }
        return out.toString();
    }

    private static void dumpJdbcTriggers(java.sql.Connection conn, String db, java.io.BufferedWriter out) throws Exception {
        List<String> names = new ArrayList<>();
        try (java.sql.Statement st = conn.createStatement();
                java.sql.ResultSet rs = st.executeQuery("SHOW TRIGGERS FROM `" + db + "`")) {
            while (rs.next()) {
                names.add(rs.getString("Trigger"));
            }
        } catch (Exception e) {
            return;
        }
        for (String name : names) {
            try (java.sql.Statement st = conn.createStatement();
                    java.sql.ResultSet rs = st.executeQuery("SHOW CREATE TRIGGER `" + db + "`.`" + name + "`")) {
                if (rs.next()) {
                    out.write("\n-- Trigger `" + name + "`\nDELIMITER ;;\n" + rs.getString(3) + ";;\nDELIMITER ;\n");
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void dumpJdbcRoutines(java.sql.Connection conn, String db, java.io.BufferedWriter out) throws Exception {
        List<String[]> routines = new ArrayList<>();
        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                "SELECT ROUTINE_NAME, ROUTINE_TYPE FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA=?")) {
            ps.setString(1, db);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    routines.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }
        } catch (Exception e) {
            return;
        }
        for (String[] routine : routines) {
            String kind = "FUNCTION".equalsIgnoreCase(routine[1]) ? "FUNCTION" : "PROCEDURE";
            try (java.sql.Statement st = conn.createStatement();
                    java.sql.ResultSet rs = st.executeQuery("SHOW CREATE " + kind + " `" + db + "`.`" + routine[0] + "`")) {
                if (rs.next()) {
                    String body = rs.getString(3).replaceAll("(?i)DEFINER\\s*=\\s*`[^`]*`@`[^`]*`\\s*", "");
                    out.write("\n-- " + kind.charAt(0) + kind.substring(1).toLowerCase(Locale.ROOT) + " `" + routine[0] + "`\nDELIMITER ;;\n" + body + ";;\nDELIMITER ;\n");
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void runMysqldump(String db, String mode, List<String> tables, Path outFile, long dumpId) throws Exception {
        String hostPort = resolveHost(db);
        String host = hostPort.contains(":") ? hostPort.substring(0, hostPort.indexOf(':')) : hostPort;
        String port = hostPort.contains(":") ? hostPort.substring(hostPort.indexOf(':') + 1) : "3306";
        String username = Registry.dbmap.get("username");
        String password = Registry.dbmap.get("password");
        if (username == null || username.isBlank()) {
            username = "root";
        }
        if (password == null) {
            throw new BadRequestException("Database credentials are not configured for dumps.");
        }
        Path cnf = Files.createTempFile("dump", ".cnf");
        try {
            String cnfBody = "[client]\nuser=\"" + username.replace("\"", "") + "\"\npassword=\""
                    + password.replace("\"", "") + "\"\nhost=\"" + host.replace("\"", "") + "\"\nport=" + port + "\n";
            Files.writeString(cnf, cnfBody, StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(cnf, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows dev: option file still works, OS ACLs apply.
            }
            List<String> cmd = new ArrayList<>();
            cmd.add("mysqldump");
            cmd.add("--defaults-extra-file=" + cnf.toString());
            cmd.add("--single-transaction");
            cmd.add("--set-gtid-purged=OFF");
            if ("FULL".equalsIgnoreCase(mode)) {
                cmd.add("--routines");
                cmd.add("--triggers");
            } else {
                // INCLUDE/EXCLUDE: triggers travel with their tables; routines may
                // reference excluded tables and break restore, so they stay out.
                cmd.add("--triggers");
                if ("EXCLUDE".equalsIgnoreCase(mode)) {
                    for (String table : tables) {
                        cmd.add("--ignore-table=" + db + "." + table);
                    }
                }
            }
            cmd.add(db);
            if ("INCLUDE".equalsIgnoreCase(mode)) {
                cmd.addAll(tables);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().remove("MYSQL_PWD");
            Process process;
            try {
                process = pb.start();
            } catch (java.io.IOException e) {
                throw new BadRequestException("mysqldump not found on this server. Dumps run on Linux hosts with mysql-client installed.");
            }
            runningProcesses.put(dumpId, process);
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread drainer = new Thread(() -> {
                try (InputStream err = process.getErrorStream()) {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = err.read(buf)) != -1) {
                        if (stderr.size() < STDERR_CAP) {
                            stderr.write(buf, 0, Math.min(read, (int) (STDERR_CAP - stderr.size())));
                        }
                    }
                } catch (Exception ignored) {
                }
            });
            drainer.setDaemon(true);
            drainer.start();
            try (OutputStream fileOut = Files.newOutputStream(outFile);
                    InputStream procOut = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = procOut.read(buf)) != -1) {
                    fileOut.write(buf, 0, read);
                }
            }
            long minutes = timeoutMinutes <= 0 ? 30 : timeoutMinutes;
            boolean done = process.waitFor(minutes, TimeUnit.MINUTES);
            drainer.join(5000);
            if (!done) {
                process.destroyForcibly();
                if (cancelRequested.containsKey(dumpId)) {
                    throw new DumpCancelled();
                }
                throw new BadRequestException("Dump timed out after " + minutes + " minutes and was stopped.");
            }
            if (cancelRequested.containsKey(dumpId)) {
                throw new DumpCancelled();
            }
            if (process.exitValue() != 0) {
                String detail = stderr.toString(StandardCharsets.UTF_8).trim();
                throw new BadRequestException("mysqldump failed" + (detail.isEmpty() ? "" : ": " + detail));
            }
            if (Files.size(outFile) == 0) {
                throw new BadRequestException("mysqldump produced an empty file.");
            }
        } finally {
            try {
                Files.deleteIfExists(cnf);
            } catch (Exception ignored) {
            }
        }
    }

    private Map<String, Object> dumpRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", num(row.get("id")));
        out.put("companyCode", str(row.get("company_code")));
        out.put("databaseName", str(row.get("database_name")));
        out.put("dbHost", str(row.get("db_host")));
        out.put("mode", str(row.get("mode")));
        out.put("tables", str(row.get("tables")));
        out.put("status", str(row.get("status")));
        out.put("requestedBy", str(row.get("requested_by")));
        String path = str(row.get("file_path"));
        out.put("hasFile", path != null && !path.isBlank());
        out.put("fileSize", num(row.get("file_size")));
        out.put("error", str(row.get("error")));
        out.put("createdAt", str(row.get("created_at")));
        out.put("startedAt", str(row.get("started_at")));
        out.put("finishedAt", str(row.get("finished_at")));
        String filePath = str(row.get("file_path"));
        String fileName = null;
        if (filePath != null && !filePath.isBlank()) {
            try {
                Path name = Paths.get(filePath).getFileName();
                fileName = name == null ? null : name.toString();
            } catch (Exception ignored) {
            }
        }
        out.put("fileName", fileName);
        return out;
    }

    /** Stop a QUEUED/RUNNING dump. RUNNING binary dumps are killed; the row becomes CANCELLED. */
    public Map<String, Object> cancelDump(long id, String requestedBy) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap("SELECT * FROM billing_common.dump_requests WHERE id=?", id);
        } catch (Exception e) {
            throw new ResourceNotFoundException("Dump request not found");
        }
        String status = str(row.get("status"));
        if ("QUEUED".equalsIgnoreCase(status)) {
            jdbcTemplate.update("UPDATE billing_common.dump_requests SET status='CANCELLED', finished_at=NOW(), error=? WHERE id=?",
                    "Cancelled by " + requestedBy, id);
        } else if ("RUNNING".equalsIgnoreCase(status)) {
            cancelRequested.put(id, requestedBy == null ? "" : requestedBy);
            Process live = runningProcesses.get(id);
            if (live != null) {
                live.destroyForcibly();
            }
        } else {
            throw new BadRequestException("Dump #" + id + " is already " + status + ".");
        }
        log.info("Dump cancelled: id={} by={}", id, requestedBy);
        return dumpRow(jdbcTemplate.queryForMap("SELECT * FROM billing_common.dump_requests WHERE id=?", id));
    }

    /** Re-queue a FAILED/CANCELLED dump as a fresh row (revalidates the database first). */
    public Map<String, Object> retryDump(long id, String requestedBy) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap("SELECT * FROM billing_common.dump_requests WHERE id=?", id);
        } catch (Exception e) {
            throw new ResourceNotFoundException("Dump request not found");
        }
        String status = str(row.get("status"));
        if (!"FAILED".equalsIgnoreCase(status) && !"CANCELLED".equalsIgnoreCase(status)) {
            throw new BadRequestException("Only failed or cancelled dumps can be retried.");
        }
        String db = str(row.get("database_name"));
        String mode = str(row.get("mode"));
        return queueDump(db, mode == null ? "FULL" : mode, splitCsv(str(row.get("tables"))), requestedBy);
    }

    /** Delete a terminal dump row (and its file, if still on disk). Running dumps must be stopped first. */
    public Map<String, Object> deleteDump(long id) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap("SELECT * FROM billing_common.dump_requests WHERE id=?", id);
        } catch (Exception e) {
            throw new ResourceNotFoundException("Dump request not found");
        }
        String status = str(row.get("status"));
        if ("QUEUED".equalsIgnoreCase(status) || "RUNNING".equalsIgnoreCase(status)) {
            throw new BadRequestException("Stop the dump before deleting it.");
        }
        String path = str(row.get("file_path"));
        try {
            if (path != null && !path.isBlank()) {
                Files.deleteIfExists(Paths.get(path));
            }
        } catch (Exception e) {
            log.warn("Dump file delete failed for id {}: {}", id, e.getMessage());
        }
        jdbcTemplate.update("DELETE FROM billing_common.dump_requests WHERE id=?", id);
        log.info("Dump row deleted: id={}", id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deleted", true);
        out.put("id", id);
        return out;
    }

    private String cleanName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (!SAFE_NAME.matcher(trimmed).matches()) {
            return null;
        }
        if (SYSTEM_DATABASES.contains(trimmed.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return trimmed;
    }

    private List<String> cleanTableList(List<String> tables) {
        List<String> out = new ArrayList<>();
        if (tables == null) {
            return out;
        }
        for (String table : tables) {
            if (table == null) {
                continue;
            }
            for (String part : table.split(",")) {
                String name = part.trim();
                if (!name.isEmpty() && SAFE_NAME.matcher(name).matches() && !out.contains(name)) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    private List<String> splitCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String part : csv.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                out.add(name);
            }
        }
        return out;
    }

    private String companyCodeFor(String db) {
        try {
            return str(jdbcTemplate.queryForObject(
                    "SELECT company_code FROM billing_common.company_registry WHERE database_name=? LIMIT 1", String.class, db));
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveHost(String db) {
        if (!"billing_common".equalsIgnoreCase(db)) {
            try {
                String host = companyRegistryService.resolveDbHostByDatabase(db);
                if (host != null && !host.isBlank()) {
                    return host.trim().replace("jdbc:mysql://", "").replace("/", "");
                }
            } catch (Exception ignored) {
            }
        }
        return defaultHost();
    }

    private String defaultHost() {
        String host = Registry.dbmap.get("url");
        if (host == null || host.isBlank()) {
            return "localhost:3306";
        }
        return host.trim().replace("jdbc:mysql://", "").replace("/", "");
    }

    private Boolean schemaExists(String db) {
        String host = resolveHost(db);
        String cleaned = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;
        if (!"localhost".equalsIgnoreCase(cleaned) && !"127.0.0.1".equals(cleaned)) {
            return null;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME=?", Integer.class, db);
            return count != null && count > 0;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean tableExists(String db, String table) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?",
                    Integer.class, db, table);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private int count(String sql) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
            return value == null ? 0 : value;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static long num(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private Path resolveDumpDir() throws Exception {
        String configured = dumpDirProp == null ? "" : dumpDirProp.trim();
        Path dir = configured.isEmpty()
                ? Paths.get(System.getProperty("java.io.tmpdir"), "billing-dumps")
                : Paths.get(configured);
        String normalized = dir.toAbsolutePath().toString().replace("\\", "/").toLowerCase(Locale.ROOT);
        if (normalized.contains("/uploads") || normalized.endsWith("/uploads")) {
            throw new IllegalStateException("app.dump.dir must not point inside the public uploads folder.");
        }
        return dir;
    }
}
