package com.billing.service.dataport;

import com.billing.dto.dataport.DataPortRow;
import com.billing.service.dataport.modules.CustomerDataPortModule;
import com.billing.service.dataport.modules.ExpenseCategoryDataPortModule;
import com.billing.service.dataport.modules.ExpenseDataPortModule;
import com.billing.service.dataport.modules.InvoiceDataPortModule;
import com.billing.service.dataport.modules.OpeningStockDataPortModule;
import com.billing.service.dataport.modules.PaymentDataPortModule;
import com.billing.service.dataport.modules.ProductCategoryDataPortModule;
import com.billing.service.dataport.modules.ProductDataPortModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sample files hi user ka pehla test hain: har sample apne columns ke format
 * me ho, Excel round-trip me na bigde, aur saare samples aapas me jude hon
 * (product ki category, invoice ka SKU/mobile, stock qty) — taaki kram se
 * import karne par sab valid aaye.
 */
class DataPortSamplesTest {

    private final ExcelReaderService excel = new ExcelReaderService();

    private final List<DataPortModule<?>> modules = List.of(
            new ProductCategoryDataPortModule(null, null, null),
            new ProductDataPortModule(null, null),
            new OpeningStockDataPortModule(null, null),
            new CustomerDataPortModule(null, null),
            new InvoiceDataPortModule(null, null),
            new PaymentDataPortModule(null, null),
            new ExpenseCategoryDataPortModule(null, null),
            new ExpenseDataPortModule(null, null));

    @Test
    void everySampleHas15To20RowsAndSurvivesExcelRoundTrip() {
        for (DataPortModule<?> module : modules) {
            List<List<String>> sample = module.sampleRows();
            assertTrue(sample.size() >= 15 && sample.size() <= 20, module.key() + " sample rows: " + sample.size());
            sample.forEach(row -> assertEquals(module.fields().size(), row.size(), module.key() + " row width"));

            ExcelReaderService.SampleFile file = excel.buildSampleWorkbook(module);
            List<String> headers = module.fields().stream().map(DataPortField::excelHeader).toList();
            List<Map<String, String>> read = excel.readRows(new MockMultipartFile("file", file.fileName(), null, file.content()), headers);
            assertEquals(sample.size(), read.size(), module.key() + " rows read back");
            for (int index = 0; index < sample.size(); index++) {
                for (int col = 0; col < headers.size(); col++) {
                    assertEquals(sample.get(index).get(col).trim(), read.get(index).get(headers.get(col)),
                            module.key() + " row " + (index + 2) + " col " + headers.get(col));
                }
            }
        }
    }

    @Test
    void sampleStartsWithSerialNumberAndOldFilesWithoutItStillRead() throws Exception {
        DataPortModule<?> module = module("expense-categories");
        ExcelReaderService.SampleFile file = excel.buildSampleWorkbook(module);
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(file.content()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("Sr. No.", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(1, (int) sheet.getRow(1).getCell(0).getNumericCellValue());
            assertEquals(module.sampleRows().size(), (int) sheet.getRow(module.sampleRows().size()).getCell(0).getNumericCellValue());
        }

        // Purani file (bina Sr. No.) bhi upload ho.
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var out = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Old");
            var header = sheet.createRow(0);
            List<String> headers = module.fields().stream().map(DataPortField::excelHeader).toList();
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Rent");
            workbook.write(out);
            List<Map<String, String>> read = excel.readRows(new MockMultipartFile("file", "old.xlsx", null, out.toByteArray()), headers);
            assertEquals(1, read.size());
            assertEquals("Rent", read.get(0).get(headers.get(0)));
        }
        assertTrue(ExcelReaderService.isSerialHeader("Sr. No."));
        assertTrue(ExcelReaderService.isSerialHeader("S.No"));
        assertTrue(ExcelReaderService.isSerialHeader("#"));
    }

    @Test
    void sampleValuesMatchTheirColumnFormat() throws Exception {
        DataPortModuleService service = new DataPortModuleService(null, excel, null, modules);
        var checkFormats = DataPortModuleService.class.getDeclaredMethod("checkFormats", DataPortRow.class, List.class);
        checkFormats.setAccessible(true);
        for (DataPortModule<?> module : modules) {
            int rowNumber = 2;
            for (List<String> values : module.sampleRows()) {
                DataPortRow row = new DataPortRow(rowNumber++, toValues(module, values));
                checkFormats.invoke(service, row, module.fields());
                // Group ki baad wali rows me header columns khaali ho sakte hain — yahan sab bhare hain.
                assertTrue(row.getValidationErrors().isEmpty(), module.key() + " row " + row.getRowNumber() + ": " + row.getValidationErrors());
            }
        }
    }

    @Test
    void samplesReferToEachOther() {
        Set<String> categoryPairs = new HashSet<>();
        module("product-categories").sampleRows().forEach(row -> categoryPairs.add(row.get(0) + "::" + row.get(1)));
        Set<String> skus = new HashSet<>();
        for (List<String> row : module("products").sampleRows()) {
            assertTrue(categoryPairs.contains(row.get(1) + "::" + row.get(2)), "product category " + row);
            skus.add(row.get(3));
        }

        Map<String, Integer> stock = new HashMap<>();
        for (List<String> row : module("opening-stock").sampleRows()) {
            assertTrue(skus.contains(row.get(3)), "stock sku " + row);
            stock.merge(row.get(3), Integer.parseInt(row.get(4)), Integer::sum);
        }

        Set<String> mobiles = new HashSet<>();
        module("customers").sampleRows().forEach(row -> mobiles.add(row.get(1).replaceAll("\\D", "").replaceFirst("^91(\\d{10})$", "$1")));
        for (List<String> row : module("invoices").sampleRows()) {
            assertTrue(mobiles.contains(row.get(2)), "invoice customer " + row);
            assertTrue(skus.contains(row.get(3)), "invoice sku " + row);
            int left = stock.merge(row.get(3), -Integer.parseInt(row.get(4)), Integer::sum);
            assertTrue(left >= 0, "invoice qty exceeds opening stock " + row);
        }
        for (List<String> row : module("payments").sampleRows()) {
            assertTrue(mobiles.contains(row.get(0)), "payment customer " + row);
        }

        Set<String> expenseCategories = new HashSet<>();
        module("expense-categories").sampleRows().forEach(row -> expenseCategories.add(row.get(0)));
        for (List<String> row : module("expenses").sampleRows()) {
            assertTrue(expenseCategories.contains(row.get(1)), "expense category " + row);
            if (!row.get(4).isEmpty()) {
                assertTrue(mobiles.contains(row.get(4)), "expense customer " + row);
            }
        }
    }

    private DataPortModule<?> module(String key) {
        return modules.stream().filter(module -> module.key().equals(key)).findFirst().orElseThrow();
    }

    private Map<String, String> toValues(DataPortModule<?> module, List<String> values) {
        Map<String, String> map = new HashMap<>();
        for (int index = 0; index < module.fields().size(); index++) {
            map.put(module.fields().get(index).key(), values.get(index));
        }
        return map;
    }
}
