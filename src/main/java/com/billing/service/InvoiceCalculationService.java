package com.billing.service;

import com.billing.entity.Product;
import com.billing.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class InvoiceCalculationService {

    public CalculationResult calculate(List<CalculationLineInput> lines, BigDecimal invoiceDiscountAmount, BigDecimal paidAmount) {
        List<PreparedLine> preparedLines = new ArrayList<>();
        BigDecimal subtotal = money(BigDecimal.ZERO);
        BigDecimal productDiscountTotal = money(BigDecimal.ZERO);

        for (CalculationLineInput line : lines) {
            Product product = line.product();
            BigDecimal unitPrice = money(line.unitPrice() != null ? line.unitPrice() : product.getSellingPrice());
            BigDecimal lineTotal = money(unitPrice.multiply(BigDecimal.valueOf(line.qty())));
            BigDecimal productDiscount = discountAmount(lineTotal, line.discountType(), line.discountValue(), line.discountPercent());
            if (productDiscount.compareTo(lineTotal) > 0) {
                throw new BadRequestException("Product discount cannot exceed line amount for " + product.getName());
            }
            BigDecimal afterProductDiscount = money(lineTotal.subtract(productDiscount));
            subtotal = subtotal.add(lineTotal);
            productDiscountTotal = productDiscountTotal.add(productDiscount);
            preparedLines.add(new PreparedLine(
                    product,
                    line.qty(),
                    unitPrice,
                    effectiveDiscountPercent(lineTotal, productDiscount, line.discountPercent()),
                    percent(line.taxPercent() != null ? line.taxPercent() : product.getTaxPercent()),
                    lineTotal,
                    productDiscount,
                    afterProductDiscount
            ));
        }

        BigDecimal afterProductDiscountSubtotal = money(subtotal.subtract(productDiscountTotal));
        BigDecimal totalBeforeInvoiceDiscount = preparedLines.stream()
                .map(line -> line.afterProductDiscount().add(percentageAmount(line.lineTotal(), line.taxPercent())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invoiceDiscount = money(invoiceDiscountAmount);
        if (invoiceDiscount.compareTo(totalBeforeInvoiceDiscount) > 0) {
            throw new BadRequestException("Invoice discount cannot exceed total amount");
        }

        List<CalculationLine> calculatedLines = new ArrayList<>();
        BigDecimal taxAmount = money(BigDecimal.ZERO);
        BigDecimal grandTotal = money(BigDecimal.ZERO);
        for (PreparedLine line : preparedLines) {
            BigDecimal lineTax = percentageAmount(line.lineTotal(), line.taxPercent());
            BigDecimal lineTotalBeforeInvoiceDiscount = money(line.afterProductDiscount().add(lineTax));
            BigDecimal invoiceDiscountShare = totalBeforeInvoiceDiscount.compareTo(BigDecimal.ZERO) > 0
                    ? money(invoiceDiscount.multiply(lineTotalBeforeInvoiceDiscount).divide(totalBeforeInvoiceDiscount, 2, RoundingMode.HALF_UP))
                    : money(BigDecimal.ZERO);
            BigDecimal lineGrandTotal = money(lineTotalBeforeInvoiceDiscount.subtract(invoiceDiscountShare));
            taxAmount = taxAmount.add(lineTax);
            grandTotal = grandTotal.add(lineGrandTotal);
            calculatedLines.add(new CalculationLine(
                    line.product(),
                    line.qty(),
                    line.unitPrice(),
                    line.discountPercent(),
                    line.taxPercent(),
                    line.lineTotal(),
                    line.productDiscount(),
                    line.afterProductDiscount(),
                    invoiceDiscountShare,
                    lineTax,
                    lineGrandTotal
            ));
        }

        BigDecimal safeGrandTotal = money(grandTotal);
        BigDecimal safePaidAmount = money(paidAmount);
        if (safePaidAmount.compareTo(safeGrandTotal) > 0) {
            throw new BadRequestException("Paid amount cannot exceed grand total");
        }

        return new CalculationResult(
                calculatedLines,
                money(subtotal),
                money(productDiscountTotal),
                afterProductDiscountSubtotal,
                invoiceDiscount,
                money(taxAmount),
                safeGrandTotal,
                safePaidAmount,
                money(safeGrandTotal.subtract(safePaidAmount))
        );
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

    private BigDecimal effectiveDiscountPercent(BigDecimal lineTotal, BigDecimal productDiscount, BigDecimal fallbackPercent) {
        if (lineTotal == null || lineTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return percent(fallbackPercent);
        }
        return percent(productDiscount.multiply(BigDecimal.valueOf(100)).divide(lineTotal, 2, RoundingMode.HALF_UP));
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    public record CalculationLineInput(Product product,
                                       Integer qty,
                                       BigDecimal unitPrice,
                                       BigDecimal taxPercent,
                                       BigDecimal discountPercent,
                                       String discountType,
                                       BigDecimal discountValue) {
    }

    private record PreparedLine(
            Product product,
            Integer qty,
            BigDecimal unitPrice,
            BigDecimal discountPercent,
            BigDecimal taxPercent,
            BigDecimal lineTotal,
            BigDecimal productDiscount,
            BigDecimal afterProductDiscount
    ) {
    }

    public record CalculationLine(
            Product product,
            Integer qty,
            BigDecimal unitPrice,
            BigDecimal discountPercent,
            BigDecimal taxPercent,
            BigDecimal lineTotal,
            BigDecimal productDiscount,
            BigDecimal afterProductDiscount,
            BigDecimal invoiceDiscountShare,
            BigDecimal taxAmount,
            BigDecimal grandTotal
    ) {
    }

    public record CalculationResult(
            List<CalculationLine> lines,
            BigDecimal subtotal,
            BigDecimal productDiscountTotal,
            BigDecimal afterProductDiscountSubtotal,
            BigDecimal invoiceDiscount,
            BigDecimal taxAmount,
            BigDecimal grandTotal,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount
    ) {
        public BigDecimal totalDiscount() {
            return productDiscountTotal.add(invoiceDiscount).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
