package com.billing.service.dataport.modules;

import com.billing.dto.dataport.DataPortRow;
import com.billing.dto.dataport.ImportResult;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.StateMaster;
import com.billing.service.CustomerService;
import com.billing.service.dataport.DataPortField;
import com.billing.service.dataport.DataPortImports;
import com.billing.service.dataport.DataPortLookups;
import com.billing.service.dataport.DataPortModule;
import com.billing.service.dataport.DataPortValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.billing.service.dataport.DataPortField.Type.BOOLEAN;
import static com.billing.service.dataport.DataPortField.Type.TEXT;

/**
 * Customers. Rules Add Customer form (frontend lib/customerValidation.ts) jaise:
 * 10-digit mobile, email format, GSTIN format, 6-digit pincode, mobile/email unique.
 */
@Component
@RequiredArgsConstructor
public class CustomerDataPortModule implements DataPortModule<CustomerDataPortModule.Context> {

    static final String GSTIN_PATTERN = "^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$";

    private final DataPortLookups lookups;
    private final CustomerService customerService;

    record Context(Map<String, Customer> byMobile, Map<String, Customer> byEmail, Map<String, StateMaster> states) {
    }

    @Override
    public String key() {
        return "customers";
    }

    @Override
    public String menuCode() {
        return "CUSTOMER_DATAPORT";
    }

    @Override
    public String title() {
        return "Customers";
    }

    @Override
    public String description() {
        return "Add customers with mobile, email, GST and address details. Invoices and payments find customers by mobile number.";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public List<DataPortField> fields() {
        return List.of(
                DataPortField.required("name", "Customer Name", TEXT, null),
                DataPortField.required("mobile", "Mobile Number", TEXT, "10 digits. +91 / spaces are removed automatically. Must be unique."),
                DataPortField.optional("email", "Email", TEXT, "Must be unique when given."),
                DataPortField.optional("gstRegistered", "GST Registered", BOOLEAN, "Blank = No."),
                DataPortField.optional("gstin", "GSTIN", TEXT, "Required when GST Registered is Yes. 15 characters, e.g. 07AABCS1234K1Z5."),
                DataPortField.optional("address", "Address", TEXT, null),
                DataPortField.optional("city", "City", TEXT, null),
                DataPortField.optional("state", "State", TEXT, "State name or code as in State Master, e.g. Delhi or DL."),
                DataPortField.optional("pincode", "Pincode", TEXT, "6 digits."),
                DataPortField.optional("active", "Active", BOOLEAN, "Blank = Yes."));
    }

    @Override
    public List<List<String>> sampleRows() {
        return List.of(
                List.of("Sharma Stationers", "9810000001", "accounts@sharmastationers.in", "Yes", "07AABCS1234K1Z5", "12, Chandni Chowk", "New Delhi", "Delhi", "110006", "Yes"),
                List.of("Priya Verma", "9810000002", "priya.verma@gmail.com", "No", "", "B-45, Sector 18", "Noida", "Uttar Pradesh", "201301", "Yes"),
                List.of("Rahul Electronics", "9810000003", "rahul.electronics@gmail.com", "Yes", "09AAFCR2034M1ZJ", "Shop 7, Naya Bazar", "Ghaziabad", "Uttar Pradesh", "201001", "Yes"),
                List.of("Amit Kumar", "9810000004", "", "No", "", "House 21, Model Town", "Panipat", "Haryana", "132103", "Yes"),
                List.of("Mehta Traders", "9810000005", "mehtatraders@yahoo.com", "Yes", "27AAACM5055K1Z7", "Office 302, Andheri East", "Mumbai", "Maharashtra", "400069", "Yes"),
                List.of("Sneha Iyer", "9810000006", "sneha.iyer@outlook.com", "No", "", "4th Cross, Indiranagar", "Bengaluru", "Karnataka", "560038", "Yes"),
                List.of("Kapoor Super Mart", "9810000007", "kapoor.mart@gmail.com", "Yes", "03AAGCK1286Q1ZV", "Mall Road", "Ludhiana", "Punjab", "141001", "Yes"),
                List.of("Rohit Singh", "+91 98100 00008", "rohit.singh@gmail.com", "No", "", "Civil Lines", "Jaipur", "Rajasthan", "302006", "Yes"),
                List.of("Gupta General Store", "9810000009", "guptastore@gmail.com", "Yes", "24AADCG0472N1Z1", "CG Road", "Ahmedabad", "Gujarat", "380009", "Yes"),
                List.of("Neha Joshi", "9810000010", "neha.joshi@gmail.com", "No", "", "Kothrud", "Pune", "Maharashtra", "411038", "Yes"),
                List.of("Tech World Solutions", "9810000011", "billing@techworld.co.in", "Yes", "33AACCT6328R1Z2", "Anna Salai", "Chennai", "Tamil Nadu", "600002", "Yes"),
                List.of("Vikas Yadav", "9810000012", "", "No", "", "Hazratganj", "Lucknow", "Uttar Pradesh", "226001", "Yes"),
                List.of("Agarwal Kitchenware", "9810000013", "agarwal.kitchen@gmail.com", "Yes", "08AAHCA7812L1ZQ", "Johari Bazar", "Jaipur", "Rajasthan", "302003", "Yes"),
                List.of("Pooja Nair", "9810000014", "pooja.nair@gmail.com", "No", "", "MG Road", "Kochi", "Kerala", "682016", "Yes"),
                List.of("Singh Hardware", "9810000015", "singh.hardware@gmail.com", "Yes", "06AAICS9087P1ZK", "Old Railway Road", "Gurugram", "Haryana", "122001", "Yes"),
                List.of("Anjali Das", "9810000016", "anjali.das@gmail.com", "No", "", "Salt Lake Sector 5", "Kolkata", "West Bengal", "700091", "Yes"),
                List.of("Fresh Basket Grocers", "9810000017", "freshbasket@gmail.com", "Yes", "36AAJCF4410D1ZC", "Banjara Hills", "Hyderabad", "Telangana", "500034", "Yes"),
                List.of("Karan Malhotra", "9810000018", "karan.malhotra@gmail.com", "No", "", "Rajouri Garden", "New Delhi", "Delhi", "110027", "No"));
    }

    @Override
    public Context buildContext(String email, Company company) {
        return new Context(lookups.customersByMobile(), lookups.customersByEmail(), lookups.activeStates());
    }

    @Override
    public void validate(List<DataPortRow> rows, Context context) {
        for (DataPortRow row : rows) {
            // Normalize pehle, taaki duplicate check "+91 98.." aur "98.." ko same maane.
            if (!row.value("mobile").isEmpty()) {
                row.getValues().put("mobile", DataPortLookups.normalizeMobile(row.value("mobile")));
            }
            if (!row.value("gstin").isEmpty()) {
                row.getValues().put("gstin", row.value("gstin").replaceAll("\\s", "").toUpperCase(Locale.ROOT));
            }
        }
        DataPortValues.markDuplicates(rows, "mobile", row -> row.value("mobile"), "Mobile number is repeated in the file");
        DataPortValues.markDuplicates(rows, "email", row -> DataPortValues.key(row.value("email")), "Email is repeated in the file");

        for (DataPortRow row : rows) {
            String mobile = row.value("mobile");
            if (!mobile.isEmpty()) {
                if (!mobile.matches("\\d{10}")) {
                    row.error("mobile", "Enter a 10-digit mobile number");
                } else if (context.byMobile().containsKey(mobile)) {
                    row.error("mobile", "This phone number is already registered");
                }
            }
            String email = row.value("email");
            if (!email.isEmpty()) {
                if (!email.matches(EMAIL_PATTERN)) {
                    row.error("email", "Enter a valid email address");
                } else if (context.byEmail().containsKey(DataPortValues.key(email))) {
                    row.error("email", "This email address is already registered");
                }
            }
            boolean gstRegistered = DataPortValues.boolOrDefault(safeBool(row.value("gstRegistered")), false);
            String gstin = row.value("gstin");
            if (gstRegistered && gstin.isEmpty()) {
                row.error("gstin", "GSTIN is required when GST Registered is Yes");
            } else if (gstRegistered && !gstin.matches(GSTIN_PATTERN)) {
                row.error("gstin", "Enter a valid 15-character GSTIN");
            } else if (!gstRegistered && !gstin.isEmpty()) {
                row.error("gstRegistered", "GSTIN is given — set GST Registered to Yes");
            }
            String state = row.value("state");
            if (!state.isEmpty()) {
                StateMaster stateMaster = context.states().get(DataPortValues.key(state));
                if (stateMaster == null) {
                    row.error("state", "State not found in State Master");
                } else {
                    row.getResolved().put("stateId", stateMaster.getId());
                    // GSTIN ke pehle 2 digit state code hote hain — state se mismatch ka pata yahan nahi
                    // chalta (State Master me GST code nahi hai), isliye sirf format check.
                }
            }
            String pincode = row.value("pincode");
            if (!pincode.isEmpty() && !pincode.matches("[1-9]\\d{5}")) {
                row.error("pincode", "Enter a 6-digit pincode");
            }
        }
    }

    private String safeBool(String value) {
        try {
            DataPortValues.bool(value);
            return value;
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    @Override
    public ImportResult importRows(String email, Company company, List<DataPortRow> validRows, Context context) {
        return DataPortImports.perRow(validRows, row -> {
            boolean gstRegistered = DataPortValues.boolOrDefault(row.value("gstRegistered"), false);
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", row.value("name"));
            param.put("mobile", row.value("mobile"));
            param.put("email", DataPortValues.blankToNull(row.value("email")));
            param.put("gstRegistered", gstRegistered);
            param.put("gstin", gstRegistered ? row.value("gstin") : null);
            param.put("address", DataPortValues.blankToNull(row.value("address")));
            param.put("city", DataPortValues.blankToNull(row.value("city")));
            param.put("stateId", row.resolvedValue("stateId"));
            param.put("pincode", DataPortValues.blankToNull(row.value("pincode")));
            param.put("country", "India");
            param.put("active", DataPortValues.boolOrDefault(row.value("active"), true));
            customerService.create(DataPortImports.sanitized(param), email);
        });
    }
}
