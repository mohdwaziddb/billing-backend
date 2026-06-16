package com.billing.service.dataport;

import com.billing.exception.BadRequestException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelReaderService {

    private final DataFormatter dataFormatter = new DataFormatter();

    public List<Map<String, String>> readRows(MultipartFile file, List<String> expectedHeaders) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please upload an Excel file");
        }

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BadRequestException("The uploaded Excel file is empty");
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            validateHeaders(headerRow, expectedHeaders);

            List<Map<String, String>> rows = new ArrayList<>();
            for (int index = headerRow.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isBlankRow(row, expectedHeaders.size())) {
                    continue;
                }

                Map<String, String> values = new LinkedHashMap<>();
                for (int cellIndex = 0; cellIndex < expectedHeaders.size(); cellIndex++) {
                    values.put(expectedHeaders.get(cellIndex), readCell(row.getCell(cellIndex)));
                }
                rows.add(values);
            }
            return rows;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Unable to read the uploaded Excel file");
        }
    }

    public byte[] buildSampleWorkbook(String sheetName, List<String> headers, List<List<String>> sampleRows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                headerRow.createCell(index).setCellValue(headers.get(index));
            }

            for (int rowIndex = 0; rowIndex < sampleRows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                List<String> values = sampleRows.get(rowIndex);
                for (int cellIndex = 0; cellIndex < headers.size(); cellIndex++) {
                    row.createCell(cellIndex).setCellValue(cellIndex < values.size() ? values.get(cellIndex) : "");
                }
            }

            for (int index = 0; index < headers.size(); index++) {
                sheet.autoSizeColumn(index);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate sample Excel file", ex);
        }
    }

    private void validateHeaders(Row headerRow, List<String> expectedHeaders) {
        if (headerRow == null) {
            throw new BadRequestException("The uploaded Excel file is missing headers");
        }

        for (int index = 0; index < expectedHeaders.size(); index++) {
            String actualHeader = readCell(headerRow.getCell(index));
            String expectedHeader = expectedHeaders.get(index);
            if (!expectedHeader.equalsIgnoreCase(actualHeader.trim())) {
                throw new BadRequestException("Invalid Excel format. Please use the downloaded sample file");
            }
        }
    }

    private boolean isBlankRow(Row row, int expectedHeaderCount) {
        for (int index = 0; index < expectedHeaderCount; index++) {
            if (!readCell(row.getCell(index)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String readCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            return dataFormatter.formatCellValue(cell).trim();
        }
        return dataFormatter.formatCellValue(cell).trim();
    }
}
