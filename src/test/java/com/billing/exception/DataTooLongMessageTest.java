package com.billing.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataTooLongMessageTest {

    @Test
    void dataTooLongShowsFieldNameInsteadOfConflictMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        DataIntegrityViolationException exception = new DataIntegrityViolationException("could not execute statement",
                new SQLException("Data truncation: Data too long for column 'category_name' at row 1"));

        String message = handler.handleDataIntegrityViolation(exception).getBody().getMessage();

        assertEquals("Category name is too long. Please shorten it and try again.", message);
    }
}
