package com.billing.service.dataport.modules;

import com.billing.dto.dataport.DataPortRow;
import com.billing.dto.dataport.ImportResult;
import com.billing.entity.Company;
import com.billing.entity.Product;
import com.billing.service.PurchaseService;
import com.billing.service.dataport.DataPortField;
import com.billing.service.dataport.DataPortImports;
import com.billing.service.dataport.DataPortLookups;
import com.billing.service.dataport.DataPortModule;
import com.billing.service.dataport.DataPortValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.billing.service.dataport.DataPortField.Type.DATE;
import static com.billing.service.dataport.DataPortField.Type.DECIMAL;
import static com.billing.service.dataport.DataPortField.Type.NUMBER;
import static com.billing.service.dataport.DataPortField.Type.TEXT;

/**
 * Opening stock / purchases. Har record PurchaseService.create se banta hai —
 * purchase number, stock batch aur stock ledger entry wahi bante hain jo
 * Purchases page se banate hain.
 */
@Component
@RequiredArgsConstructor
public class OpeningStockDataPortModule implements DataPortModule<OpeningStockDataPortModule.Context> {

    private static final List<String> HEADER_FIELDS = List.of("purchaseDate", "supplierName", "remarks");

    private final DataPortLookups lookups;
    private final PurchaseService purchaseService;

    record Context(Map<String, Product> productsBySku, Map<String, String> existingLines) {
    }

    @Override
    public String key() {
        return "opening-stock";
    }

    @Override
    public String menuCode() {
        return "STOCK_DATAPORT";
    }

    @Override
    public String title() {
        return "Opening Stock";
    }

    @Override
    public String description() {
        return "Load opening stock or past purchases by SKU. Each purchase creates stock batches and stock ledger entries, exactly like the Purchases page.";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String groupField() {
        return "purchaseRef";
    }

    @Override
    public List<DataPortField> fields() {
        return List.of(
                DataPortField.optional("purchaseRef", "Purchase Ref", TEXT, "Rows with the same ref become one purchase (one bill). Blank = each row is its own purchase."),
                DataPortField.required("purchaseDate", "Purchase Date", DATE, "Same for every row of a purchase."),
                DataPortField.optional("supplierName", "Supplier Name", TEXT, null),
                DataPortField.required("sku", "SKU", TEXT, "Must match an existing active product."),
                DataPortField.required("qty", "Qty", NUMBER, "1 or more."),
                DataPortField.required("purchaseRate", "Purchase Rate", DECIMAL, "Cost per unit (before GST)."),
                DataPortField.required("sellingRate", "Selling Rate", DECIMAL, "Must not be lower than purchase rate."),
                DataPortField.optional("remarks", "Remarks", TEXT, null));
    }

    @Override
    public List<String> notes() {
        return List.of("Import Products first. A purchase is saved only when all of its rows are valid.");
    }

    @Override
    public List<List<String>> sampleRows() {
        return List.of(
                List.of("OS-001", "01-04-2026", "Kumar Stationery Wholesale", "DP-NB-001", "200", "38", "55", "Opening stock"),
                List.of("OS-001", "01-04-2026", "Kumar Stationery Wholesale", "DP-NB-002", "150", "62", "90", "Opening stock"),
                List.of("OS-001", "01-04-2026", "Kumar Stationery Wholesale", "DP-PEN-001", "120", "70", "100", "Opening stock"),
                List.of("OS-001", "01-04-2026", "Kumar Stationery Wholesale", "DP-PEN-002", "100", "35", "50", "Opening stock"),
                List.of("OS-001", "01-04-2026", "Kumar Stationery Wholesale", "DP-FILE-001", "80", "45", "70", "Opening stock"),
                List.of("OS-001", "01-04-2026", "Kumar Stationery Wholesale", "DP-ART-001", "60", "90", "130", "Opening stock"),
                List.of("OS-002", "01-04-2026", "Digital Hub Distributors", "DP-MOB-001", "50", "650", "999", "Opening stock"),
                List.of("OS-002", "01-04-2026", "Digital Hub Distributors", "DP-MOB-002", "100", "60", "199", "Opening stock"),
                List.of("OS-002", "01-04-2026", "Digital Hub Distributors", "DP-CMP-001", "40", "420", "699", "Opening stock"),
                List.of("OS-002", "01-04-2026", "Digital Hub Distributors", "DP-CMP-002", "30", "380", "599", "Opening stock"),
                List.of("OS-002", "01-04-2026", "Digital Hub Distributors", "DP-AUD-001", "25", "1100", "1799", "Opening stock"),
                List.of("OS-003", "02-04-2026", "Annapurna Foods", "DP-SNK-001", "60", "210", "299", ""),
                List.of("OS-003", "02-04-2026", "Annapurna Foods", "DP-BEV-001", "50", "260", "349", ""),
                List.of("OS-003", "02-04-2026", "Annapurna Foods", "DP-STP-001", "40", "520", "699", ""),
                List.of("OS-004", "03-04-2026", "HomeStyle Traders", "DP-CKW-001", "20", "780", "1199", ""),
                List.of("OS-004", "03-04-2026", "HomeStyle Traders", "DP-STO-001", "35", "260", "399", ""),
                List.of("OS-004", "03-04-2026", "HomeStyle Traders", "DP-CLN-001", "60", "140", "199", ""),
                List.of("", "03-04-2026", "Wellness Distributors", "DP-SKN-001", "70", "95", "149", "Single-line purchase"),
                List.of("", "03-04-2026", "Wellness Distributors", "DP-HAI-001", "50", "180", "260", "Single-line purchase"),
                List.of("", "04-04-2026", "Taparia Dealer", "DP-TOL-001", "15", "420", "650", "Single-line purchase"));
    }

    @Override
    public Context buildContext(String email, Company company) {
        return new Context(lookups.productsBySku(company), lookups.existingPurchaseLines(company));
    }

    @Override
    public void validate(List<DataPortRow> rows, Context context) {
        DataPortValues.markDuplicates(rows, "sku",
                row -> row.value("purchaseRef").isEmpty() ? null : DataPortValues.key(row.value("purchaseRef")) + "::" + DataPortValues.key(row.value("sku")),
                "Same SKU is repeated inside this purchase — combine the quantity in one row");
        GroupRules.sameHeaderValues(rows, groupField(), HEADER_FIELDS);

        for (DataPortRow row : rows) {
            Product product = context.productsBySku().get(DataPortValues.key(row.value("sku")));
            if (!row.value("sku").isEmpty()) {
                if (product == null) {
                    row.error("sku", "No product with this SKU — import Products first");
                } else if (!product.isActive()) {
                    row.error("sku", "Product is inactive");
                } else {
                    row.getResolved().put("productId", product.getId());
                }
            }
            Integer qty = DataPortValues.integer(row.value("qty"));
            if (qty != null && qty < 1) {
                row.error("qty", "Qty must be 1 or more");
            }
            BigDecimal purchaseRate = DataPortValues.decimal(row.value("purchaseRate"));
            BigDecimal sellingRate = DataPortValues.decimal(row.value("sellingRate"));
            if (purchaseRate != null && purchaseRate.signum() < 0) {
                row.error("purchaseRate", "Purchase rate must be 0 or more");
            }
            if (sellingRate != null && sellingRate.signum() < 0) {
                row.error("sellingRate", "Selling rate must be 0 or more");
            }
            if (purchaseRate != null && sellingRate != null && sellingRate.compareTo(purchaseRate) < 0) {
                row.error("sellingRate", "Selling rate cannot be lower than purchase rate");
            }
            LocalDate date = DataPortValues.date(row.value("purchaseDate"));
            if (date != null && date.isAfter(LocalDate.now())) {
                row.error("purchaseDate", "Purchase date cannot be in the future");
            }
            // Wahi file dobara upload: same date + supplier + SKU + qty + rate pehle se hai to stock dobara na badhe.
            String existingNo = context.existingLines().get(DataPortLookups.purchaseLineKey(
                    date, row.value("supplierName"), row.resolvedValue("productId"), qty, purchaseRate));
            if (date != null && existingNo != null) {
                row.error("sku", "Already imported in " + existingNo + " (same date, supplier, SKU, qty and rate)");
            }
        }
    }

    @Override
    public ImportResult importRows(String email, Company company, List<DataPortRow> validRows, Context context) {
        return DataPortImports.perGroup(validRows, groupField(), group -> {
            DataPortRow first = group.get(0);
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("purchaseDate", DataPortValues.date(first.value("purchaseDate")).toString());
            param.put("supplierName", DataPortValues.blankToNull(first.value("supplierName")));
            param.put("remarks", DataPortValues.blankToNull(first.value("remarks")));
            List<Map<String, Object>> items = new ArrayList<>();
            for (DataPortRow row : group) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("productId", row.resolvedValue("productId"));
                item.put("qty", DataPortValues.integer(row.value("qty")));
                item.put("purchaseRate", DataPortValues.money(row.value("purchaseRate")));
                item.put("sellingRate", DataPortValues.money(row.value("sellingRate")));
                items.add(item);
            }
            param.put("items", items);
            purchaseService.create(DataPortImports.sanitized(param), email);
        });
    }
}
