package com.billing.saas.service;

import com.billing.saas.dto.dashboard.DashboardSummaryResponse;
import com.billing.saas.entity.Company;
import com.billing.saas.entity.Invoice;
import com.billing.saas.repository.InvoiceRepository;
import com.billing.saas.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        List<Invoice> invoices = invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company);

        BigDecimal revenue = invoices.stream()
                .map(Invoice::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = invoices.stream()
                .map(Invoice::getBalanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DashboardSummaryResponse.builder()
                .totalInvoices(invoiceRepository.countByCompany(company))
                .totalProducts(productRepository.countByCompany(company))
                .totalRevenue(revenue)
                .outstandingBalance(outstanding)
                .build();
    }
}
