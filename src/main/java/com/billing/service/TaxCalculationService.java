package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Product;
import com.billing.entity.TaxMaster;
import com.billing.entity.enums.TaxType;
import com.billing.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaxCalculationService {

    public TaxDocumentCalculation calculate(Company company,
                                            Customer customer,
                                            List<TaxDocumentLineInput> lines,
                                            BigDecimal invoiceDiscountAmount,
                                            BigDecimal paidAmount) {
        // Composition scheme: dealer customer se GST collect nahi kar sakta (Bill of Supply).
        boolean chargeTax = !company.isCompositionScheme();
        if (chargeTax) {
            requireStateForGst(company, customer, lines);
        }

        List<PreparedLine> preparedLines = new ArrayList<>();
        BigDecimal subtotal = money(BigDecimal.ZERO);
        BigDecimal productDiscountTotal = money(BigDecimal.ZERO);

        for (TaxDocumentLineInput line : lines) {
            Product product = line.product();
            TaxMaster taxMaster = line.taxMaster();
            BigDecimal unitPrice = money(line.unitPrice());
            int qty = line.qty() == null ? 0 : line.qty();
            if (qty < 1) {
                throw new BadRequestException("Quantity must be at least 1 for " + product.getName());
            }
            BigDecimal lineTotal = money(unitPrice.multiply(BigDecimal.valueOf(qty)));
            BigDecimal productDiscount = discountAmount(lineTotal, line.discountType(), line.discountValue(), line.discountPercent());
            if (productDiscount.compareTo(lineTotal) > 0) {
                throw new BadRequestException("Product discount cannot exceed line amount for " + product.getName());
            }
            BigDecimal afterProductDiscount = money(lineTotal.subtract(productDiscount));
            subtotal = subtotal.add(lineTotal);
            productDiscountTotal = productDiscountTotal.add(productDiscount);
            preparedLines.add(new PreparedLine(product, taxMaster, qty, unitPrice, lineTotal, productDiscount, afterProductDiscount));
        }

        BigDecimal afterProductDiscountSubtotal = money(subtotal.subtract(productDiscountTotal));
        BigDecimal invoiceDiscount = money(invoiceDiscountAmount);
        if (invoiceDiscount.compareTo(afterProductDiscountSubtotal) > 0) {
            throw new BadRequestException("Invoice discount cannot exceed taxable value");
        }

        boolean sameState = isSameState(company, customer);
        List<TaxDocumentLine> calculatedLines = new ArrayList<>();
        BigDecimal taxableAmount = money(BigDecimal.ZERO);
        BigDecimal cgstTotal = money(BigDecimal.ZERO);
        BigDecimal sgstTotal = money(BigDecimal.ZERO);
        BigDecimal igstTotal = money(BigDecimal.ZERO);
        BigDecimal grandTotal = money(BigDecimal.ZERO);

        List<BigDecimal> invoiceDiscountShares = splitInvoiceDiscount(preparedLines, invoiceDiscount, afterProductDiscountSubtotal);
        for (int index = 0; index < preparedLines.size(); index++) {
            PreparedLine line = preparedLines.get(index);
            BigDecimal invoiceDiscountShare = invoiceDiscountShares.get(index);
            BigDecimal lineTaxableAmount = money(line.afterProductDiscount().subtract(invoiceDiscountShare));
            TaxBreakup breakup = breakup(line.taxMaster(), chargeTax && line.product().isTaxable(), lineTaxableAmount, sameState);
            BigDecimal lineGrandTotal = money(lineTaxableAmount.add(breakup.cgstAmount()).add(breakup.sgstAmount()).add(breakup.igstAmount()));
            taxableAmount = taxableAmount.add(lineTaxableAmount);
            cgstTotal = cgstTotal.add(breakup.cgstAmount());
            sgstTotal = sgstTotal.add(breakup.sgstAmount());
            igstTotal = igstTotal.add(breakup.igstAmount());
            grandTotal = grandTotal.add(lineGrandTotal);

            calculatedLines.add(new TaxDocumentLine(
                    line.product(),
                    line.taxMaster(),
                    line.qty(),
                    line.unitPrice(),
                    line.lineTotal(),
                    line.productDiscount(),
                    invoiceDiscountShare,
                    lineTaxableAmount,
                    breakup.rate(),
                    breakup.cgstRate(),
                    breakup.cgstAmount(),
                    breakup.sgstRate(),
                    breakup.sgstAmount(),
                    breakup.igstRate(),
                    breakup.igstAmount(),
                    lineGrandTotal
            ));
        }

        // Bill ka total poore rupaye tak (HALF_UP); farq "Round Off" line me jaata hai
        // (frontend InvoiceCalculationService jaisa). Line amounts aur GST paise tak hi rehte hain.
        BigDecimal unroundedTotal = money(grandTotal);
        BigDecimal roundOff = money(unroundedTotal.setScale(0, RoundingMode.HALF_UP).subtract(unroundedTotal));
        BigDecimal safeGrandTotal = money(unroundedTotal.add(roundOff));
        BigDecimal safePaidAmount = money(paidAmount);
        if (safePaidAmount.compareTo(safeGrandTotal) > 0) {
            throw new BadRequestException("Paid amount cannot exceed grand total");
        }

        return new TaxDocumentCalculation(
                calculatedLines,
                money(subtotal),
                money(productDiscountTotal.add(invoiceDiscount)),
                money(taxableAmount),
                money(cgstTotal),
                money(sgstTotal),
                money(igstTotal),
                roundOff,
                safeGrandTotal,
                safePaidAmount,
                money(safeGrandTotal.subtract(safePaidAmount))
        );
    }

    private void requireStateForGst(Company company, Customer customer, List<TaxDocumentLineInput> lines) {
        boolean hasGst = lines.stream().anyMatch(line ->
                line.product().isTaxable()
                        && line.taxMaster() != null
                        && line.taxMaster().getTaxType() == TaxType.GST
                        && money(line.taxMaster().getRate()).compareTo(BigDecimal.ZERO) > 0
        );
        if (!hasGst) {
            return;
        }
        if (company.getStateMaster() == null) {
            throw new BadRequestException("Company state is required for GST calculation");
        }
        if (customer.getStateMaster() == null) {
            throw new BadRequestException("Customer state is required for GST calculation");
        }
    }

    private boolean isSameState(Company company, Customer customer) {
        if (company.getStateMaster() == null || customer.getStateMaster() == null) {
            return false;
        }
        return company.getStateMaster().getId().equals(customer.getStateMaster().getId());
    }

    private TaxBreakup breakup(TaxMaster taxMaster, boolean taxable, BigDecimal taxableAmount, boolean sameState) {
        BigDecimal safeTaxableAmount = money(taxableAmount);
        if (!taxable || taxMaster == null || money(taxMaster.getRate()).compareTo(BigDecimal.ZERO) == 0) {
            return new TaxBreakup(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal rate = percent(taxMaster.getRate());
        if (taxMaster.getTaxType() != TaxType.GST) {
            BigDecimal amount = percentageAmount(safeTaxableAmount, rate);
            return new TaxBreakup(rate, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), rate, amount);
        }

        if (sameState) {
            // Display rate rounded (0.25% -> 0.13%), par AMOUNT full rate / 200 se,
            // taaki cgst+sgst = asli tax rahe (frontend InvoiceCalculationService jaisa).
            BigDecimal halfRate = percent(rate.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
            BigDecimal halfAmount = money(safeTaxableAmount.multiply(rate).divide(BigDecimal.valueOf(200), 2, RoundingMode.HALF_UP));
            BigDecimal cgstAmount = halfAmount;
            BigDecimal sgstAmount = halfAmount;
            return new TaxBreakup(rate, halfRate, cgstAmount, halfRate, sgstAmount,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal igstAmount = percentageAmount(safeTaxableAmount, rate);
        return new TaxBreakup(rate,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                rate,
                igstAmount);
    }

    /**
     * Largest-remainder split: har line ka share paise me floor, phir bache
     * paise sabse bade fraction wali lines ko. Shares ka sum EXACTLY invoice
     * discount hota hai (per-line HALF_UP me paise drift hote the) aur frontend
     * InvoiceCalculationService.ts se match karta hai.
     */
    private List<BigDecimal> splitInvoiceDiscount(List<PreparedLine> lines,
                                                  BigDecimal invoiceDiscount,
                                                  BigDecimal afterProductDiscountSubtotal) {
        List<BigDecimal> shares = new ArrayList<>();
        if (afterProductDiscountSubtotal.compareTo(BigDecimal.ZERO) <= 0 || invoiceDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            for (int index = 0; index < lines.size(); index++) {
                shares.add(money(BigDecimal.ZERO));
            }
            return shares;
        }
        List<BigDecimal> fractions = new ArrayList<>();
        BigDecimal flooredTotal = BigDecimal.ZERO;
        for (PreparedLine line : lines) {
            BigDecimal raw = invoiceDiscount.multiply(line.afterProductDiscount())
                    .divide(afterProductDiscountSubtotal, 10, RoundingMode.HALF_UP);
            BigDecimal floored = raw.setScale(2, RoundingMode.FLOOR);
            shares.add(floored);
            fractions.add(raw.subtract(floored));
            flooredTotal = flooredTotal.add(floored);
        }
        int remainderPaise = invoiceDiscount.subtract(flooredTotal).movePointRight(2).intValue();
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            order.add(index);
        }
        // Stable sort: barabar fraction par pehli line ko pehle paisa (frontend jaisa).
        order.sort((left, right) -> fractions.get(right).compareTo(fractions.get(left)));
        for (int position = 0; position < order.size() && remainderPaise > 0; position++, remainderPaise--) {
            int index = order.get(position);
            shares.set(index, shares.get(index).add(new BigDecimal("0.01")));
        }
        return shares;
    }

    private BigDecimal discountAmount(BigDecimal base, String discountType, BigDecimal discountValue, BigDecimal discountPercent) {
        BigDecimal safeBase = money(base);
        BigDecimal safeValue = money(discountValue);
        if ("FIXED".equalsIgnoreCase(discountType)) {
            return money(safeValue.min(safeBase));
        }
        return percentageAmount(safeBase, discountValue != null ? discountValue : discountPercent);
    }

    private BigDecimal percentageAmount(BigDecimal base, BigDecimal percent) {
        return money(base.multiply(percent(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    public record TaxDocumentLineInput(Product product,
                                       TaxMaster taxMaster,
                                       Integer qty,
                                       BigDecimal unitPrice,
                                       BigDecimal discountPercent,
                                       String discountType,
                                       BigDecimal discountValue) {
    }

    private record PreparedLine(Product product,
                                TaxMaster taxMaster,
                                Integer qty,
                                BigDecimal unitPrice,
                                BigDecimal lineTotal,
                                BigDecimal productDiscount,
                                BigDecimal afterProductDiscount) {
    }

    public record TaxBreakup(BigDecimal rate,
                             BigDecimal cgstRate,
                             BigDecimal cgstAmount,
                             BigDecimal sgstRate,
                             BigDecimal sgstAmount,
                             BigDecimal igstRate,
                             BigDecimal igstAmount) {
    }

    public record TaxDocumentLine(Product product,
                                  TaxMaster taxMaster,
                                  Integer qty,
                                  BigDecimal unitPrice,
                                  BigDecimal lineTotal,
                                  BigDecimal productDiscount,
                                  BigDecimal invoiceDiscountShare,
                                  BigDecimal taxableAmount,
                                  BigDecimal taxRate,
                                  BigDecimal cgstRate,
                                  BigDecimal cgstAmount,
                                  BigDecimal sgstRate,
                                  BigDecimal sgstAmount,
                                  BigDecimal igstRate,
                                  BigDecimal igstAmount,
                                  BigDecimal grandTotal) {
    }

    public record TaxDocumentCalculation(List<TaxDocumentLine> lines,
                                         BigDecimal subtotal,
                                         BigDecimal discountAmount,
                                         BigDecimal taxableAmount,
                                         BigDecimal cgstTotal,
                                         BigDecimal sgstTotal,
                                         BigDecimal igstTotal,
                                         BigDecimal roundOff,
                                         BigDecimal grandTotal,
                                         BigDecimal paidAmount,
                                         BigDecimal outstandingAmount) {
        public BigDecimal totalTaxAmount() {
            return cgstTotal.add(sgstTotal).add(igstTotal).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
