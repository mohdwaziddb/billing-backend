package com.billing.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentNumberServiceTest {

    @Test
    void financialYearStartsInApril() {
        assertEquals("2526", DocumentNumberService.financialYearKey(LocalDate.of(2026, 3, 31)));
        assertEquals("2627", DocumentNumberService.financialYearKey(LocalDate.of(2026, 4, 1)));
        assertEquals("2627", DocumentNumberService.financialYearKey(LocalDate.of(2027, 3, 31)));
        assertEquals("9900", DocumentNumberService.financialYearKey(LocalDate.of(2099, 4, 1)));
    }
}
