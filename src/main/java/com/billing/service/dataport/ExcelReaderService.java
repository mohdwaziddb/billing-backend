package com.billing.service.dataport;

import com.billing.exception.BadRequestException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExcelReaderService {

    // Excel export (billing-frontend/src/lib/excelExport.ts) wale hi rang.
    private static final byte[] HEADER_BLUE = {(byte) 0x1F, (byte) 0x4E, (byte) 0x78};
    private static final byte[] LIGHT_BLUE = {(byte) 0xDD, (byte) 0xEB, (byte) 0xF7};
    private static final byte[] BORDER_GRAY = {(byte) 0x9C, (byte) 0xA3, (byte) 0xAF};

    private final DataFormatter dataFormatter = new DataFormatter(Locale.ENGLISH);

    static final String SERIAL_HEADER = "Sr. No.";

    public record SampleFile(String fileName, byte[] content) {
    }

    public List<Map<String, String>> readRows(MultipartFile file, List<String> expectedHeaders) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please upload an Excel file");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.isEmpty() && !name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new BadRequestException("Only Excel files (.xlsx / .xls) are supported. Please use the downloaded sample file.");
        }

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BadRequestException("The uploaded Excel file is empty");
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            // Sample ka pehla column "Sr. No." hai — wo data nahi, chhod do (purani bina Sr. No. wali file bhi chale).
            int offset = headerRow != null && isSerialHeader(readCell(headerRow.getCell(0))) ? 1 : 0;
            validateHeaders(headerRow, expectedHeaders, offset);

            List<Map<String, String>> rows = new ArrayList<>();
            for (int index = headerRow.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isBlankRow(row, expectedHeaders.size(), offset)) {
                    continue;
                }

                Map<String, String> values = new LinkedHashMap<>();
                for (int cellIndex = 0; cellIndex < expectedHeaders.size(); cellIndex++) {
                    values.put(expectedHeaders.get(cellIndex), readCell(row.getCell(cellIndex + offset)));
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

    /**
     * Sample workbook: Sheet 1 = data (styled header, freeze, filter, text
     * format taaki mobile/GSTIN/date Excel badle nahi, Yes/No dropdown),
     * Sheet 2 = Instructions (har column ka matlab + allowed values).
     * Upload sirf Sheet 1 ki pehli row ko header maanta hai.
     */
    public SampleFile buildSampleWorkbook(DataPortModule<?> module) {
        List<DataPortField> fields = module.fields();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = bodyStyle(workbook, false);
            CellStyle stripeStyle = bodyStyle(workbook, true);

            CellStyle serialStyle = serialStyle(workbook, false);
            CellStyle serialStripeStyle = serialStyle(workbook, true);

            XSSFSheet sheet = workbook.createSheet(safeSheetName(module.title()));
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);
            // Column A = Sr. No. (sab exports jaisa); upload me ye column chhod diya jata hai.
            Cell serialHeader = headerRow.createCell(0);
            serialHeader.setCellValue(SERIAL_HEADER);
            serialHeader.setCellStyle(headerStyle);
            for (int index = 0; index < fields.size(); index++) {
                Cell cell = headerRow.createCell(index + 1);
                cell.setCellValue(fields.get(index).excelHeader());
                cell.setCellStyle(headerStyle);
            }

            List<List<String>> sampleRows = module.sampleRows();
            for (int rowIndex = 0; rowIndex < sampleRows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                boolean stripe = rowIndex % 2 == 1;
                Cell serialCell = row.createCell(0);
                serialCell.setCellValue(rowIndex + 1);
                serialCell.setCellStyle(stripe ? serialStripeStyle : serialStyle);
                List<String> values = sampleRows.get(rowIndex);
                for (int cellIndex = 0; cellIndex < fields.size(); cellIndex++) {
                    Cell cell = row.createCell(cellIndex + 1);
                    cell.setCellValue(cellIndex < values.size() ? values.get(cellIndex) : "");
                    cell.setCellStyle(stripe ? stripeStyle : textStyle);
                }
            }

            sheet.setColumnWidth(0, 9 * 256);
            for (int index = 1; index <= fields.size(); index++) {
                // Khaali neeche wali rows bhi text format me (user apna data daale to 0 na kate).
                sheet.setDefaultColumnStyle(index, textStyle);
                sheet.autoSizeColumn(index);
                int width = Math.min(Math.max(sheet.getColumnWidth(index) + 768, 14 * 256), 42 * 256);
                sheet.setColumnWidth(index, width);
            }
            sheet.createFreezePane(1, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(1, sampleRows.size()), 0, fields.size()));
            addDropdowns(sheet, fields);
            buildInstructions(workbook, module, headerStyle, textStyle);

            workbook.write(outputStream);
            return new SampleFile(module.key() + "-dataport-sample.xlsx", outputStream.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate sample Excel file", ex);
        }
    }

    private void addDropdowns(XSSFSheet sheet, List<DataPortField> fields) {
        XSSFDataValidationHelper helper = new XSSFDataValidationHelper(sheet);
        for (int index = 0; index < fields.size(); index++) {
            DataPortField field = fields.get(index);
            List<String> options = field.type() == DataPortField.Type.BOOLEAN ? List.of("Yes", "No")
                    : field.type() == DataPortField.Type.CHOICE ? field.options() : List.of();
            if (options.isEmpty()) {
                continue;
            }
            var constraint = helper.createExplicitListConstraint(options.toArray(new String[0]));
            // +1: column A Sr. No. hai.
            var validation = helper.createValidation(constraint, new CellRangeAddressList(1, DataPortModuleService.MAX_ROWS, index + 1, index + 1));
            validation.setShowErrorBox(true);
            validation.createErrorBox("Invalid value", "Choose one of: " + String.join(", ", options));
            sheet.addValidationData(validation);
        }
    }

    private void buildInstructions(XSSFWorkbook workbook, DataPortModule<?> module, CellStyle headerStyle, CellStyle textStyle) {
        Sheet sheet = workbook.createSheet("Instructions");
        CellStyle titleStyle = workbook.createCellStyle();
        XSSFFont titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setColor(new XSSFColor(HEADER_BLUE, null));
        titleStyle.setFont(titleFont);

        int rowIndex = 0;
        Row title = sheet.createRow(rowIndex++);
        title.createCell(0).setCellValue(module.title() + " — how to fill this file");
        title.getCell(0).setCellStyle(titleStyle);
        sheet.createRow(rowIndex++).createCell(0).setCellValue(module.description());
        sheet.createRow(rowIndex++).createCell(0).setCellValue(
                "Fill the first sheet only. Keep the header row as it is. Columns marked * are required. Dates: dd-mm-yyyy (e.g. 15-10-2026).");
        sheet.createRow(rowIndex++).createCell(0).setCellValue(
                "Sr. No. (column A) is only for counting rows — it is not imported, so you may leave it blank.");
        for (String note : module.notes()) {
            sheet.createRow(rowIndex++).createCell(0).setCellValue("• " + note);
        }
        rowIndex++;

        Row header = sheet.createRow(rowIndex++);
        String[] headers = {"Column", "Required", "Format / allowed values", "Notes"};
        for (int index = 0; index < headers.length; index++) {
            header.createCell(index).setCellValue(headers[index]);
            header.getCell(index).setCellStyle(headerStyle);
        }
        for (DataPortField field : module.fields()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(field.excelHeader());
            row.createCell(1).setCellValue(field.required() ? "Yes" : "No");
            row.createCell(2).setCellValue(formatHint(field));
            row.createCell(3).setCellValue(field.hint() == null ? "" : field.hint());
            for (int index = 0; index < 4; index++) {
                row.getCell(index).setCellStyle(textStyle);
            }
        }
        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 11 * 256);
        sheet.setColumnWidth(2, 34 * 256);
        sheet.setColumnWidth(3, 70 * 256);
    }

    private String formatHint(DataPortField field) {
        return switch (field.type()) {
            case NUMBER -> "Whole number";
            case DECIMAL -> "Number (e.g. 1250.50)";
            case DATE -> "Date dd-mm-yyyy";
            case BOOLEAN -> "Yes / No";
            case CHOICE -> String.join(" / ", field.options());
            default -> "Text";
        };
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, null));
        style.setFont(font);
        // SOLID fill: gray125 (Excel ka reserved pattern) se header chaukhana dikhta tha.
        style.setFillForegroundColor(new XSSFColor(HEADER_BLUE, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        border(style);
        return style;
    }

    private CellStyle serialStyle(XSSFWorkbook workbook, boolean stripe) {
        XSSFCellStyle style = (XSSFCellStyle) bodyStyle(workbook, stripe);
        style.setDataFormat(workbook.createDataFormat().getFormat("0"));
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle bodyStyle(XSSFWorkbook workbook, boolean stripe) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("@"));
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        if (stripe) {
            style.setFillForegroundColor(new XSSFColor(LIGHT_BLUE, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        border(style);
        return style;
    }

    private void border(XSSFCellStyle style) {
        XSSFColor gray = new XSSFColor(BORDER_GRAY, null);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(gray);
        style.setBottomBorderColor(gray);
        style.setLeftBorderColor(gray);
        style.setRightBorderColor(gray);
    }

    private String safeSheetName(String title) {
        String cleaned = title.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    private void validateHeaders(Row headerRow, List<String> expectedHeaders, int offset) {
        if (headerRow == null) {
            throw new BadRequestException("The uploaded Excel file is missing headers");
        }

        for (int index = 0; index < expectedHeaders.size(); index++) {
            String actualHeader = readCell(headerRow.getCell(index + offset));
            String expectedHeader = expectedHeaders.get(index);
            // "*" (required mark) hata ke compare: user ne star mita diya ho to bhi chale.
            if (!normalizeHeader(expectedHeader).equals(normalizeHeader(actualHeader))) {
                throw new BadRequestException("Invalid Excel format: column " + (index + offset + 1) + " should be \""
                        + expectedHeader + "\". Please use the downloaded sample file.");
            }
        }
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.replace("*", "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** "Sr. No.", "Sr No", "S.No", "Serial No", "#" — sab serial column maane jaate hain. */
    static boolean isSerialHeader(String header) {
        String compact = header == null ? "" : header.replaceAll("[^A-Za-z#]", "").toLowerCase(Locale.ROOT);
        return compact.equals("srno") || compact.equals("sno") || compact.equals("serialno") || compact.equals("slno") || compact.equals("#");
    }

    // Sirf Sr. No. bhara ho (data khaali) to wo row blank hi hai.
    private boolean isBlankRow(Row row, int expectedHeaderCount, int offset) {
        for (int index = 0; index < expectedHeaderCount; index++) {
            if (!readCell(row.getCell(index + offset)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String readCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        // Date cell ko Excel ke locale format (10/15/26) me nahi, seedha dd-MM-yyyy me lo.
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().format(DataPortValues.DISPLAY_DATE);
        }
        return dataFormatter.formatCellValue(cell).trim();
    }
}
