package com.billing.saas.service;

import com.billing.saas.dto.invoice.InvoiceItemRequest;
import com.billing.saas.dto.invoice.InvoiceItemResponse;
import com.billing.saas.dto.invoice.InvoiceRequest;
import com.billing.saas.dto.invoice.InvoiceResponse;
import com.billing.saas.entity.Company;
import com.billing.saas.entity.Customer;
import com.billing.saas.entity.Invoice;
import com.billing.saas.entity.InvoiceItem;
import com.billing.saas.entity.Product;
import com.billing.saas.entity.enums.InvoiceStatus;
import com.billing.saas.exception.BadRequestException;
import com.billing.saas.exception.ResourceNotFoundException;
import com.billing.saas.repository.InvoiceRepository;
import com.billing.saas.repository.PaymentRepository;
import com.billing.saas.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final AccessControlService accessControlService;
    private final CustomerService customerService;

    @Transactional
    public InvoiceResponse create(String email, InvoiceRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = customerService.getCustomerOrThrow(company, request.getCustomerId());
        if (!customer.isActive()) {
            throw new BadRequestException("Inactive customer cannot be invoiced");
        }

        Invoice invoice = Invoice.builder()
                .company(company)
                .customer(customer)
                .invoiceNo(generateInvoiceNumber(company))
                .invoiceDate(request.getInvoiceDate())
                .paidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .build();

        applyInvoiceState(invoice, request, customer, true);
        invoiceRepository.save(invoice);
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(String email, Long customerId) {
        Company company = accessControlService.getCurrentCompany(email);
        List<Invoice> invoices = customerId == null
                ? invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company)
                : invoiceRepository.findByCompanyAndCustomerOrderByInvoiceDateDescIdDesc(
                        company,
                        customerService.getCustomerOrThrow(company, customerId)
                );

        return invoices.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(String email, Long invoiceId) {
        Company company = accessControlService.getCurrentCompany(email);
        return toResponse(getInvoiceOrThrow(company, invoiceId));
    }

    @Transactional
    public InvoiceResponse update(String email, Long invoiceId, InvoiceRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Invoice invoice = getInvoiceOrThrow(company, invoiceId);
        Customer newCustomer = customerService.getCustomerOrThrow(company, request.getCustomerId());
        if (!newCustomer.isActive()) {
            throw new BadRequestException("Inactive customer cannot be invoiced");
        }

        restoreInvoiceEffects(invoice);
        invoice.setCustomer(newCustomer);
        invoice.setInvoiceDate(request.getInvoiceDate());
        applyInvoiceState(invoice, request, newCustomer, false);
        invoiceRepository.save(invoice);
        return toResponse(invoice);
    }

    @Transactional
    public void delete(String email, Long invoiceId) {
        Company company = accessControlService.getCurrentCompany(email);
        Invoice invoice = getInvoiceOrThrow(company, invoiceId);
        if (paymentRepository.existsByInvoice(invoice) || scale(invoice.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("Delete linked payments before deleting this invoice");
        }
        restoreInvoiceEffects(invoice);
        invoiceRepository.delete(invoice);
    }

    public Invoice getInvoiceOrThrow(Company company, Long invoiceId) {
        return invoiceRepository.findByIdAndCompany(invoiceId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    public void applyPayment(Invoice invoice, BigDecimal amount) {
        BigDecimal nextPaid = scale(invoice.getPaidAmount().add(amount));
        if (nextPaid.compareTo(scale(invoice.getTotalAmount())) > 0) {
            throw new BadRequestException("Payment exceeds invoice balance");
        }
        invoice.setPaidAmount(nextPaid);
        invoice.setBalanceAmount(scale(invoice.getTotalAmount().subtract(nextPaid)));
        invoice.setPaymentStatus(resolveStatus(invoice.getBalanceAmount(), invoice.getPaidAmount()));
        invoiceRepository.save(invoice);
    }

    public void reversePayment(Invoice invoice, BigDecimal amount) {
        BigDecimal nextPaid = scale(invoice.getPaidAmount().subtract(amount));
        if (nextPaid.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Payment reversal would make paid amount negative");
        }
        invoice.setPaidAmount(nextPaid);
        invoice.setBalanceAmount(scale(invoice.getTotalAmount().subtract(nextPaid)));
        invoice.setPaymentStatus(resolveStatus(invoice.getBalanceAmount(), invoice.getPaidAmount()));
        invoiceRepository.save(invoice);
    }

    private void applyInvoiceState(Invoice invoice, InvoiceRequest request, Customer customer, boolean creating) {
        List<InvoiceItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;

        for (InvoiceItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findByIdAndCompany(itemRequest.getProductId(), invoice.getCompany())
                    .orElseThrow(() -> new BadRequestException("Product not found for id " + itemRequest.getProductId()));

            if (!product.isActive()) {
                throw new BadRequestException("Inactive product cannot be invoiced: " + product.getName());
            }
            if (product.getStockQty() < itemRequest.getQty()) {
                throw new BadRequestException("Insufficient stock for product " + product.getName());
            }

            BigDecimal unitPrice = scale(product.getSellingPrice());
            BigDecimal lineBase = scale(unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQty())));
            BigDecimal itemDiscount = percentageAmount(lineBase, itemRequest.getDiscountPercent());
            BigDecimal taxableAmount = scale(lineBase.subtract(itemDiscount));
            BigDecimal itemTax = percentageAmount(taxableAmount, product.getTaxPercent());
            BigDecimal lineTotal = scale(taxableAmount.add(itemTax));

            subtotal = subtotal.add(lineBase);
            taxAmount = taxAmount.add(itemTax);

            product.setStockQty(product.getStockQty() - itemRequest.getQty());
            productRepository.save(product);

            items.add(InvoiceItem.builder()
                    .invoice(invoice)
                    .product(product)
                    .qty(itemRequest.getQty())
                    .price(unitPrice)
                    .discountPercent(scale(itemRequest.getDiscountPercent()))
                    .taxPercent(scale(product.getTaxPercent()))
                    .lineTotal(lineTotal)
                    .build());
        }

        BigDecimal itemTotals = items.stream()
                .map(InvoiceItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountAmount = scale(request.getDiscountAmount());

        if (discountAmount.compareTo(itemTotals) > 0) {
            throw new BadRequestException("Invoice discount cannot exceed line totals");
        }

        BigDecimal totalAmount = scale(itemTotals.subtract(discountAmount));
        BigDecimal paidAmount = creating ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : scale(invoice.getPaidAmount());
        if (paidAmount.compareTo(totalAmount) > 0) {
            throw new BadRequestException("Existing payments exceed updated invoice total");
        }

        BigDecimal previousBalance = creating ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : scale(invoice.getBalanceAmount());
        BigDecimal balanceAmount = scale(totalAmount.subtract(paidAmount));

        invoice.getItems().clear();
        invoice.getItems().addAll(items);
        invoice.setSubtotal(scale(subtotal));
        invoice.setTaxAmount(scale(taxAmount));
        invoice.setDiscountAmount(discountAmount);
        invoice.setTotalAmount(totalAmount);
        invoice.setPaidAmount(paidAmount);
        invoice.setBalanceAmount(balanceAmount);
        invoice.setPaymentStatus(resolveStatus(balanceAmount, paidAmount));

        customerService.increaseBalance(customer, balanceAmount.subtract(previousBalance));
    }

    private void restoreInvoiceEffects(Invoice invoice) {
        Customer oldCustomer = invoice.getCustomer();
        customerService.decreaseBalance(oldCustomer, scale(invoice.getBalanceAmount()));

        for (InvoiceItem item : invoice.getItems()) {
            Product product = item.getProduct();
            product.setStockQty(product.getStockQty() + item.getQty());
            productRepository.save(product);
        }

        invoice.getItems().clear();
        invoice.setSubtotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setBalanceAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setPaymentStatus(InvoiceStatus.UNPAID);
    }

    private InvoiceStatus resolveStatus(BigDecimal balanceAmount, BigDecimal paidAmount) {
        if (balanceAmount.compareTo(BigDecimal.ZERO) == 0) {
            return InvoiceStatus.PAID;
        }
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            return InvoiceStatus.PARTIAL;
        }
        return InvoiceStatus.UNPAID;
    }

    private String generateInvoiceNumber(Company company) {
        Invoice lastInvoice = invoiceRepository.findTopByCompanyOrderByIdDesc(company).orElse(null);
        long nextSequence = lastInvoice == null ? 1L : lastInvoice.getId() + 1L;
        return "INV-" + company.getId() + "-" + String.format("%05d", nextSequence);
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNo(invoice.getInvoiceNo())
                .customerId(invoice.getCustomer().getId())
                .customerName(invoice.getCustomer().getName())
                .subtotal(scale(invoice.getSubtotal()))
                .taxAmount(scale(invoice.getTaxAmount()))
                .discountAmount(scale(invoice.getDiscountAmount()))
                .totalAmount(scale(invoice.getTotalAmount()))
                .paidAmount(scale(invoice.getPaidAmount()))
                .balanceAmount(scale(invoice.getBalanceAmount()))
                .paymentStatus(invoice.getPaymentStatus().name())
                .invoiceDate(invoice.getInvoiceDate())
                .createdAt(invoice.getCreatedAt())
                .createdBy(invoice.getCreatedBy())
                .items(invoice.getItems().stream().map(item -> InvoiceItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .qty(item.getQty())
                        .price(scale(item.getPrice()))
                        .discountPercent(scale(item.getDiscountPercent()))
                        .taxPercent(scale(item.getTaxPercent()))
                        .lineTotal(scale(item.getLineTotal()))
                        .build()).toList())
                .build();
    }

    private BigDecimal percentageAmount(BigDecimal base, BigDecimal percent) {
        return scale(base.multiply(scale(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
