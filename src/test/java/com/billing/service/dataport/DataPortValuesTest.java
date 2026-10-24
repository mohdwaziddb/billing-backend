package com.billing.service.dataport;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataPortValuesTest {

    @Test
    void datesAreParsedStrictly() {
        assertEquals(LocalDate.of(2026, 10, 15), DataPortValues.date("15-10-2026"));
        assertEquals(LocalDate.of(2026, 10, 5), DataPortValues.date("5/10/2026"));
        assertEquals(LocalDate.of(2026, 10, 15), DataPortValues.date("2026-10-15"));
        assertEquals(LocalDate.of(2026, 10, 15), DataPortValues.date("15-Oct-2026"));
        assertNull(DataPortValues.date("31-02-2026"), "invalid day must not roll over to 28 Feb");
        assertNull(DataPortValues.date("15/13/2026"));
        assertNull(DataPortValues.date("tomorrow"));
    }

    @Test
    void numbersAcceptIndianFormatting() {
        assertEquals(new BigDecimal("125000.50"), DataPortValues.decimal("₹1,25,000.50"));
        assertEquals(12, DataPortValues.integer("12"));
        assertEquals(12, DataPortValues.integer("12.0"));
        assertNull(DataPortValues.integer("2.5"));
        assertNull(DataPortValues.decimal("abc"));
    }

    @Test
    void yesNoValues() {
        assertEquals(true, DataPortValues.bool("YES"));
        assertEquals(false, DataPortValues.bool("0"));
        assertNull(DataPortValues.bool(" "));
        assertThrows(IllegalArgumentException.class, () -> DataPortValues.bool("maybe"));
    }

    @Test
    void mobileNormalizesLikeTheCustomerForm() {
        assertEquals("9876543210", DataPortLookups.normalizeMobile("+91 98765-43210"));
        assertEquals("9876543210", DataPortLookups.normalizeMobile("9876543210"));
    }
}
