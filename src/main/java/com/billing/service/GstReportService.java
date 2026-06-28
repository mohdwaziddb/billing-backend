package com.billing.service;

import com.billing.dto.gst.GstCustomerWiseRowResponse;
import com.billing.dto.gst.GstHsnSummaryRowResponse;
import com.billing.dto.gst.GstInvoiceWiseRowResponse;
import com.billing.dto.gst.GstMonthWiseRowResponse;
import com.billing.dto.gst.GstReportResponse;
import com.billing.dto.gst.GstTaxWiseRowResponse;
import com.billing.entity.Company;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.entity.User;
import com.billing.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GstReportService {

    private static final DateTimeFormatter MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy");

    private final AccessControlService accessControlService;
    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public GstReportResponse summary(String email, LocalDate startDate, LocalDate endDate) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);

        List<Invoice> invoices = invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company).stream()
                .filter(invoice -> !invoice.isDeleted())
                .filter(invoice -> inRange(invoice.getInvoiceDate(), startDate, endDate))
                .toList();

        List<GstInvoiceWiseRowResponse> invoiceWise = invoices.stream()
                .map(this::toInvoiceWiseRow)
                .toList();

        List<GstCustomerWiseRowResponse> customerWise = customerWise(invoices);
        List<GstMonthWiseRowResponse> monthWise = monthWise(invoices);
        List<GstTaxWiseRowResponse> taxWise = taxWise(invoices);
        List<GstHsnSummaryRowResponse> hsnSummary = hsnSummary(invoices);

        BigDecimal taxableAmount = invoices.stream().map(Invoice::getTaxableAmount).reduce(zero(), this::add);
        BigDecimal cgstAmount = invoices.stream().map(Invoice::getCgstTotal).reduce(zero(), this::add);
        BigDecimal sgstAmount = invoices.stream().map(Invoice::getSgstTotal).reduce(zero(), this::add);
        BigDecimal igstAmount = invoices.stream().map(Invoice::getIgstTotal).reduce(zero(), this::add);
        BigDecimal grandTotal = invoices.stream().map(Invoice::getGrandTotal).reduce(zero(), this::add);

        return GstReportResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalInvoices(invoices.size())
                .taxableAmount(scale(taxableAmount))
                .cgstAmount(scale(cgstAmount))
                .sgstAmount(scale(sgstAmount))
                .igstAmount(scale(igstAmount))
                .totalTaxAmount(scale(cgstAmount.add(sgstAmount).add(igstAmount)))
                .grandTotal(scale(grandTotal))
                .invoiceWise(invoiceWise)
                .customerWise(customerWise)
                .monthWise(monthWise)
                .taxWise(taxWise)
                .hsnSummary(hsnSummary)
                .build();
    }

    private GstInvoiceWiseRowResponse toInvoiceWiseRow(Invoice invoice) {
        return GstInvoiceWiseRowResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceNo(invoice.getInvoiceNo())
                .invoiceDate(invoice.getInvoiceDate())
                .customerId(invoice.getCustomer().getId())
                .customerName(invoice.getCustomer().getName())
                .customerState(invoice.getCustomer().getState())
                .customerStateId(invoice.getCustomer().getStateMaster() != null ? invoice.getCustomer().getStateMaster().getId() : null)
                .customerGstin(invoice.getCustomer().getGstin() != null ? invoice.getCustomer().getGstin() : invoice.getCustomer().getGstNo())
                .taxableAmount(scale(invoice.getTaxableAmount()))
                .cgstAmount(scale(invoice.getCgstTotal()))
                .sgstAmount(scale(invoice.getSgstTotal()))
                .igstAmount(scale(invoice.getIgstTotal()))
                .totalTaxAmount(scale(invoice.getCgstTotal().add(invoice.getSgstTotal()).add(invoice.getIgstTotal())))
                .grandTotal(scale(invoice.getGrandTotal()))
                .build();
    }

    private List<GstCustomerWiseRowResponse> customerWise(List<Invoice> invoices) {
        Map<Long, CustomerAccumulator> totals = new LinkedHashMap<>();
        for (Invoice invoice : invoices) {
            Long customerId = invoice.getCustomer().getId();
            CustomerAccumulator accumulator = totals.computeIfAbsent(customerId, ignored -> new CustomerAccumulator(
                    customerId,
                    invoice.getCustomer().getName(),
                    invoice.getCustomer().getGstin() != null ? invoice.getCustomer().getGstin() : invoice.getCustomer().getGstNo()
            ));
            accumulator.taxableAmount = add(accumulator.taxableAmount, invoice.getTaxableAmount());
            accumulator.cgstAmount = add(accumulator.cgstAmount, invoice.getCgstTotal());
            accumulator.sgstAmount = add(accumulator.sgstAmount, invoice.getSgstTotal());
            accumulator.igstAmount = add(accumulator.igstAmount, invoice.getIgstTotal());
            accumulator.grandTotal = add(accumulator.grandTotal, invoice.getGrandTotal());
            accumulator.invoiceCount++;
        }
        return totals.values().stream()
                .map(item -> GstCustomerWiseRowResponse.builder()
                        .customerId(item.customerId)
                        .customerName(item.customerName)
                        .customerGstin(item.customerGstin)
                        .taxableAmount(scale(item.taxableAmount))
                        .cgstAmount(scale(item.cgstAmount))
                        .sgstAmount(scale(item.sgstAmount))
                        .igstAmount(scale(item.igstAmount))
                        .totalTaxAmount(scale(item.cgstAmount.add(item.sgstAmount).add(item.igstAmount)))
                        .grandTotal(scale(item.grandTotal))
                        .invoiceCount(item.invoiceCount)
                        .build())
                .sorted(Comparator.comparing(GstCustomerWiseRowResponse::getCustomerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<GstMonthWiseRowResponse> monthWise(List<Invoice> invoices) {
        Map<YearMonth, MonthAccumulator> totals = new LinkedHashMap<>();
        for (Invoice invoice : invoices) {
            YearMonth month = YearMonth.from(invoice.getInvoiceDate());
            MonthAccumulator accumulator = totals.computeIfAbsent(month, ignored -> new MonthAccumulator(month));
            accumulator.taxableAmount = add(accumulator.taxableAmount, invoice.getTaxableAmount());
            accumulator.cgstAmount = add(accumulator.cgstAmount, invoice.getCgstTotal());
            accumulator.sgstAmount = add(accumulator.sgstAmount, invoice.getSgstTotal());
            accumulator.igstAmount = add(accumulator.igstAmount, invoice.getIgstTotal());
            accumulator.grandTotal = add(accumulator.grandTotal, invoice.getGrandTotal());
            accumulator.invoiceCount++;
        }
        return totals.values().stream()
                .sorted(Comparator.comparing(item -> item.month))
                .map(item -> GstMonthWiseRowResponse.builder()
                        .monthKey(item.month.toString())
                        .monthLabel(item.month.format(MONTH_LABEL_FORMAT))
                        .taxableAmount(scale(item.taxableAmount))
                        .cgstAmount(scale(item.cgstAmount))
                        .sgstAmount(scale(item.sgstAmount))
                        .igstAmount(scale(item.igstAmount))
                        .totalTaxAmount(scale(item.cgstAmount.add(item.sgstAmount).add(item.igstAmount)))
                        .grandTotal(scale(item.grandTotal))
                        .invoiceCount(item.invoiceCount)
                        .build())
                .toList();
    }

    private List<GstTaxWiseRowResponse> taxWise(List<Invoice> invoices) {
        Map<String, TaxAccumulator> totals = new LinkedHashMap<>();
        for (Invoice invoice : invoices) {
            for (InvoiceItem item : invoice.getItems()) {
                String key = (item.getTaxMaster() != null ? item.getTaxMaster().getId() : 0L) + "|" + safe(item.getTaxName()) + "|" + scale(item.getTaxRate());
                TaxAccumulator accumulator = totals.computeIfAbsent(key, ignored -> new TaxAccumulator(item));
                accumulator.taxableAmount = add(accumulator.taxableAmount, item.getTaxableAmount());
                accumulator.cgstAmount = add(accumulator.cgstAmount, item.getCgstAmount());
                accumulator.sgstAmount = add(accumulator.sgstAmount, item.getSgstAmount());
                accumulator.igstAmount = add(accumulator.igstAmount, item.getIgstAmount());
                accumulator.grandAmount = add(accumulator.grandAmount, item.getGrandAmount());
                accumulator.lineCount++;
            }
        }
        return totals.values().stream()
                .map(item -> GstTaxWiseRowResponse.builder()
                        .taxMasterId(item.taxMasterId)
                        .taxName(item.taxName)
                        .taxCode(item.taxCode)
                        .taxType(item.taxType)
                        .taxRate(scale(item.taxRate))
                        .taxableAmount(scale(item.taxableAmount))
                        .cgstAmount(scale(item.cgstAmount))
                        .sgstAmount(scale(item.sgstAmount))
                        .igstAmount(scale(item.igstAmount))
                        .totalTaxAmount(scale(item.cgstAmount.add(item.sgstAmount).add(item.igstAmount)))
                        .grandAmount(scale(item.grandAmount))
                        .lineCount(item.lineCount)
                        .build())
                .sorted(Comparator.comparing(GstTaxWiseRowResponse::getTaxName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private List<GstHsnSummaryRowResponse> hsnSummary(List<Invoice> invoices) {
        Map<String, HsnAccumulator> totals = new LinkedHashMap<>();
        for (Invoice invoice : invoices) {
            for (InvoiceItem item : invoice.getItems()) {
                String hsnCode = safe(item.getHsnCode()).isBlank() ? "UNSPECIFIED" : item.getHsnCode().trim();
                HsnAccumulator accumulator = totals.computeIfAbsent(hsnCode, ignored -> new HsnAccumulator(hsnCode));
                accumulator.taxableAmount = add(accumulator.taxableAmount, item.getTaxableAmount());
                accumulator.cgstAmount = add(accumulator.cgstAmount, item.getCgstAmount());
                accumulator.sgstAmount = add(accumulator.sgstAmount, item.getSgstAmount());
                accumulator.igstAmount = add(accumulator.igstAmount, item.getIgstAmount());
                accumulator.grandAmount = add(accumulator.grandAmount, item.getGrandAmount());
                accumulator.totalQuantity += item.getQty() == null ? 0 : item.getQty();
                accumulator.lineCount++;
            }
        }
        return totals.values().stream()
                .map(item -> GstHsnSummaryRowResponse.builder()
                        .hsnCode(item.hsnCode)
                        .taxableAmount(scale(item.taxableAmount))
                        .cgstAmount(scale(item.cgstAmount))
                        .sgstAmount(scale(item.sgstAmount))
                        .igstAmount(scale(item.igstAmount))
                        .totalTaxAmount(scale(item.cgstAmount.add(item.sgstAmount).add(item.igstAmount)))
                        .grandAmount(scale(item.grandAmount))
                        .totalQuantity(item.totalQuantity)
                        .lineCount(item.lineCount)
                        .build())
                .sorted(Comparator.comparing(GstHsnSummaryRowResponse::getHsnCode, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean inRange(LocalDate value, LocalDate startDate, LocalDate endDate) {
        return value != null
                && (startDate == null || !value.isBefore(startDate))
                && (endDate == null || !value.isAfter(endDate));
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        return scale(left).add(scale(right));
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? zero() : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class CustomerAccumulator {
        private final Long customerId;
        private final String customerName;
        private final String customerGstin;
        private BigDecimal taxableAmount = BigDecimal.ZERO;
        private BigDecimal cgstAmount = BigDecimal.ZERO;
        private BigDecimal sgstAmount = BigDecimal.ZERO;
        private BigDecimal igstAmount = BigDecimal.ZERO;
        private BigDecimal grandTotal = BigDecimal.ZERO;
        private long invoiceCount = 0;

        private CustomerAccumulator(Long customerId, String customerName, String customerGstin) {
            this.customerId = customerId;
            this.customerName = customerName;
            this.customerGstin = customerGstin;
        }
    }

    private static final class MonthAccumulator {
        private final YearMonth month;
        private BigDecimal taxableAmount = BigDecimal.ZERO;
        private BigDecimal cgstAmount = BigDecimal.ZERO;
        private BigDecimal sgstAmount = BigDecimal.ZERO;
        private BigDecimal igstAmount = BigDecimal.ZERO;
        private BigDecimal grandTotal = BigDecimal.ZERO;
        private long invoiceCount = 0;

        private MonthAccumulator(YearMonth month) {
            this.month = month;
        }
    }

    private static final class TaxAccumulator {
        private final Long taxMasterId;
        private final String taxName;
        private final String taxCode;
        private final String taxType;
        private final BigDecimal taxRate;
        private BigDecimal taxableAmount = BigDecimal.ZERO;
        private BigDecimal cgstAmount = BigDecimal.ZERO;
        private BigDecimal sgstAmount = BigDecimal.ZERO;
        private BigDecimal igstAmount = BigDecimal.ZERO;
        private BigDecimal grandAmount = BigDecimal.ZERO;
        private long lineCount = 0;

        private TaxAccumulator(InvoiceItem item) {
            this.taxMasterId = item.getTaxMaster() != null ? item.getTaxMaster().getId() : null;
            this.taxName = item.getTaxName();
            this.taxCode = item.getTaxMaster() != null ? item.getTaxMaster().getTaxCode() : null;
            this.taxType = item.getTaxMaster() != null && item.getTaxMaster().getTaxType() != null ? item.getTaxMaster().getTaxType().name() : null;
            this.taxRate = item.getTaxRate();
        }
    }

    private static final class HsnAccumulator {
        private final String hsnCode;
        private BigDecimal taxableAmount = BigDecimal.ZERO;
        private BigDecimal cgstAmount = BigDecimal.ZERO;
        private BigDecimal sgstAmount = BigDecimal.ZERO;
        private BigDecimal igstAmount = BigDecimal.ZERO;
        private BigDecimal grandAmount = BigDecimal.ZERO;
        private long totalQuantity = 0;
        private long lineCount = 0;

        private HsnAccumulator(String hsnCode) {
            this.hsnCode = hsnCode;
        }
    }
}
