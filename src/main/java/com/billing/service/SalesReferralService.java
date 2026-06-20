package com.billing.service;

import com.billing.dto.referral.SalesReferralInvoiceResponse;
import com.billing.dto.referral.SalesReferralReportResponse;
import com.billing.dto.referral.SalesReferralUserSummaryResponse;
import com.billing.entity.Company;
import com.billing.entity.Invoice;
import com.billing.entity.User;
import com.billing.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SalesReferralService {

    private final AccessControlService accessControlService;
    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public SalesReferralReportResponse report(String email, LocalDate startDate, LocalDate endDate) {
        Company company = accessControlService.getCurrentCompany(email);
        DateRange range = normalizeRange(startDate, endDate);
        YearMonth thisMonth = YearMonth.now();

        List<Invoice> referredInvoices = invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company).stream()
                .filter(invoice -> invoice.getReferByUser() != null)
                .filter(invoice -> isWithinRange(invoice.getInvoiceDate(), range.startDate(), range.endDate()))
                .toList();

        Map<Long, List<Invoice>> invoicesByUser = referredInvoices.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        invoice -> invoice.getReferByUser().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        List<SalesReferralUserSummaryResponse> users = invoicesByUser.values().stream()
                .map(this::toUserSummary)
                .sorted(Comparator.comparing(SalesReferralUserSummaryResponse::getTotalRevenue).reversed())
                .toList();

        List<SalesReferralInvoiceResponse> referredInvoiceRows = referredInvoices.stream()
                .map(this::toInvoiceResponse)
                .toList();
        List<SalesReferralInvoiceResponse> thisMonthRows = referredInvoices.stream()
                .filter(invoice -> YearMonth.from(invoice.getInvoiceDate()).equals(thisMonth))
                .map(this::toInvoiceResponse)
                .toList();

        return SalesReferralReportResponse.builder()
                .startDate(range.startDate())
                .endDate(range.endDate())
                .totalReferredInvoices(referredInvoices.size())
                .totalReferredRevenue(sum(referredInvoices, AmountKind.TOTAL))
                .thisMonthReferredRevenue(sum(thisMonthRows.stream().map(row -> row.getAmount()).toList()))
                .topPerformer(users.isEmpty() ? null : users.get(0))
                .users(users)
                .topContributors(users.stream().limit(10).toList())
                .referredInvoices(referredInvoiceRows)
                .thisMonthInvoices(thisMonthRows)
                .build();
    }

    private SalesReferralUserSummaryResponse toUserSummary(List<Invoice> invoices) {
        User user = invoices.get(0).getReferByUser();
        BigDecimal totalRevenue = sum(invoices, AmountKind.TOTAL);
        long count = invoices.size();
        return SalesReferralUserSummaryResponse.builder()
                .userId(user.getId())
                .userName(user.getFullName())
                .username(user.getUsername())
                .totalInvoices(count)
                .totalRevenue(totalRevenue)
                .paidRevenue(sum(invoices, AmountKind.PAID))
                .outstandingRevenue(sum(invoices, AmountKind.OUTSTANDING))
                .averageInvoiceValue(count == 0 ? zero() : totalRevenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP))
                .invoices(invoices.stream().map(this::toInvoiceResponse).toList())
                .build();
    }

    private SalesReferralInvoiceResponse toInvoiceResponse(Invoice invoice) {
        return SalesReferralInvoiceResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceNo(invoice.getInvoiceNo())
                .customerName(invoice.getCustomer().getName())
                .referByUserName(invoice.getReferByUser() != null ? invoice.getReferByUser().getFullName() : null)
                .referByUserMobileNumber(invoice.getReferByUser() != null ? invoice.getReferByUser().getMobileNumber() : null)
                .invoiceDate(invoice.getInvoiceDate())
                .amount(scale(invoice.getTotalAmount()))
                .paidAmount(scale(invoice.getPaidAmount()))
                .outstandingAmount(scale(invoice.getBalanceAmount()))
                .build();
    }

    private boolean isWithinRange(LocalDate value, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && value.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !value.isAfter(endDate);
    }

    private DateRange normalizeRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return new DateRange(endDate, startDate);
        }
        return new DateRange(startDate, endDate);
    }

    private BigDecimal sum(List<Invoice> invoices, AmountKind kind) {
        return sum(invoices.stream()
                .map(invoice -> switch (kind) {
                    case TOTAL -> invoice.getTotalAmount();
                    case PAID -> invoice.getPaidAmount();
                    case OUTSTANDING -> invoice.getBalanceAmount();
                })
                .toList());
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return scale(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    private enum AmountKind {
        TOTAL,
        PAID,
        OUTSTANDING
    }
}
