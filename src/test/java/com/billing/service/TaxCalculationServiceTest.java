package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Product;
import com.billing.entity.StateMaster;
import com.billing.entity.TaxMaster;
import com.billing.entity.enums.TaxType;
import com.billing.service.TaxCalculationService.TaxDocumentCalculation;
import com.billing.service.TaxCalculationService.TaxDocumentLineInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TaxCalculationServiceTest {

    private final TaxCalculationService service = new TaxCalculationService();

    private static final StateMaster DELHI = StateMaster.builder().id(1L).build();
    private static final StateMaster UP = StateMaster.builder().id(2L).build();

    @Test
    void invoiceDiscountSharesAddUpExactly() {
        // 3 x 100 par 10 discount: 3.3333.. har line. Purana per-line HALF_UP 9.99 deta tha.
        List<TaxDocumentLineInput> lines = List.of(line(null, "100.00"), line(null, "100.00"), line(null, "100.00"));

        TaxDocumentCalculation result = service.calculate(company(DELHI), customer(DELHI), lines, money("10.00"), BigDecimal.ZERO);

        assertEquals(money("3.34"), result.lines().get(0).invoiceDiscountShare());
        assertEquals(money("3.33"), result.lines().get(1).invoiceDiscountShare());
        assertEquals(money("3.33"), result.lines().get(2).invoiceDiscountShare());
        assertEquals(money("290.00"), result.taxableAmount());
        assertEquals(money("290.00"), result.grandTotal());
    }

    @Test
    void sameStateSplitUsesFullRateForAmounts() {
        // 0.25% GST: display rate 0.13% (rounded), par CGST+SGST amount = asli 2.50.
        TaxDocumentCalculation result = service.calculate(company(DELHI), customer(DELHI),
                List.of(line(gst("0.25"), "1000.00")), BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(money("0.13"), result.lines().get(0).cgstRate());
        assertEquals(money("1.25"), result.cgstTotal());
        assertEquals(money("1.25"), result.sgstTotal());
        // Total poore rupaye tak: 1002.50 -> 1003.00, farq round-off me.
        assertEquals(money("0.50"), result.roundOff());
        assertEquals(money("1003.00"), result.grandTotal());
    }

    @Test
    void payFullWithFrontendGrandTotalIsAccepted() {
        // Frontend InvoiceCalculationService.ts isi input par 342.20 -> round-off ke
        // baad 342.00 nikalta hai. Purana backend 342.21 deta tha, isliye
        // "Pay full" par invoice PARTIAL (0.01 baaki) reh jata tha.
        List<TaxDocumentLineInput> lines = List.of(line(gst("18"), "100.00"), line(gst("18"), "100.00"), line(gst("18"), "100.00"));
        BigDecimal frontendGrandTotal = money("342.00");

        TaxDocumentCalculation result = assertDoesNotThrow(() ->
                service.calculate(company(DELHI), customer(DELHI), lines, money("10.00"), frontendGrandTotal));

        assertEquals(frontendGrandTotal, result.grandTotal());
        assertEquals(money("-0.20"), result.roundOff());
        assertEquals(money("0.00"), result.outstandingAmount());
    }

    @Test
    void interStateUsesIgst() {
        TaxDocumentCalculation result = service.calculate(company(DELHI), customer(UP),
                List.of(line(gst("18"), "100.00")), BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(money("0.00"), result.cgstTotal());
        assertEquals(money("18.00"), result.igstTotal());
        assertEquals(money("118.00"), result.grandTotal());
    }

    @Test
    void compositionDealerChargesNoTaxAndNeedsNoState() {
        // Composition scheme = Bill of Supply: 18% product par bhi tax 0; customer ka state na ho tab bhi chale.
        Company composition = Company.builder().id(1L).name("Co").compositionScheme(true).build();
        TaxDocumentCalculation result = service.calculate(composition, customer(null),
                List.of(line(gst("18"), "1000.00")), BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(money("0.00"), result.totalTaxAmount());
        assertEquals(money("1000.00"), result.grandTotal());
    }

    private static TaxDocumentLineInput line(TaxMaster taxMaster, String unitPrice) {
        Product product = Product.builder().id(1L).name("Item").taxable(taxMaster != null).taxMaster(taxMaster).build();
        return new TaxDocumentLineInput(product, taxMaster, 1, new BigDecimal(unitPrice), BigDecimal.ZERO, "FIXED", BigDecimal.ZERO);
    }

    private static TaxMaster gst(String rate) {
        return TaxMaster.builder().id(1L).taxType(TaxType.GST).rate(new BigDecimal(rate)).build();
    }

    private static Company company(StateMaster state) {
        return Company.builder().id(1L).name("Co").stateMaster(state).build();
    }

    private static Customer customer(StateMaster state) {
        return Customer.builder().id(1L).name("Cust").stateMaster(state).build();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
