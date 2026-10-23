package com.billing.service;

import com.billing.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BizioConsoleServiceTest {

    private static final String CURRENT_DB = "tenant_b";

    private final BizioConsoleService service = new BizioConsoleService(null, null);
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        // Server par dikhne wale schemas: purana unregistered "billing" DB bhi.
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("tenant_a", "tenant_b", "billing", "mysql"));
    }

    @Test
    void blocksServerWideShowStatements() {
        assertBlocked("SHOW DATABASES");
        assertBlocked("SHOW PROCESSLIST");
        assertBlocked("SHOW GRANTS");
    }

    @Test
    void blocksOtherSchemasEvenIfNotRegistered() {
        assertBlocked("SELECT * FROM billing.users");
        assertBlocked("SHOW TABLES FROM tenant_a");
        assertBlocked("SHOW TABLE STATUS IN billing");
    }

    @Test
    void blocksLockingAndSleepFunctions() {
        assertBlocked("SELECT * FROM users FOR UPDATE");
        assertBlocked("SELECT SLEEP(10)");
    }

    @Test
    void allowsNormalReadsOnCurrentDatabase() {
        assertEquals("SELECT u.full_name FROM users u LIMIT 500",
                service.validateAndFinalize("SELECT u.full_name FROM users u", CURRENT_DB, jdbcTemplate));
        assertEquals("SHOW TABLES", service.validateAndFinalize("SHOW TABLES", CURRENT_DB, jdbcTemplate));
        assertEquals("SELECT * FROM tenant_b.users LIMIT 500",
                service.validateAndFinalize("SELECT * FROM tenant_b.users", CURRENT_DB, jdbcTemplate));
    }

    private void assertBlocked(String sql) {
        assertThrows(BadRequestException.class, () -> service.validateAndFinalize(sql, CURRENT_DB, jdbcTemplate), sql);
    }
}
