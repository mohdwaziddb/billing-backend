package com.billing.service;

import com.billing.dto.report.HierarchyNodeResponse;
import com.billing.dto.report.HierarchyMetricsResponse;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.Payment;
import com.billing.entity.Product;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.repository.CustomerRepository;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentRepository;
import com.billing.repository.ProductRepository;
import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ManagementHierarchyService {

    private final AccessControlService accessControlService;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<HierarchyNodeResponse> roots(String email, String role, String status, String search, String startDate, String endDate, String manager) {
        Company company = accessControlService.getCurrentCompany(email);
        List<User> users = filteredUsers(company, role, status, search);
        MetricContext context = metricContext(company, users, parseDate(startDate), parseDate(endDate));
        List<User> owners = users.stream().filter(user -> user.getRole() == RoleName.OWNER).toList();
        if (!owners.isEmpty()) {
            return owners.stream().map(user -> toNode(user, hasRole(users, RoleName.ADMIN), null, context, scopeFor(user, users))).toList();
        }
        return users.stream()
                .filter(user -> user.getRole() == RoleName.ADMIN || user.getRole() == RoleName.USER)
                .filter(user -> !hasText(manager) || user.getRole() != RoleName.ADMIN || contains(user.getFullName(), manager.trim().toLowerCase(Locale.ROOT)))
                .map(user -> toNode(user, user.getRole() == RoleName.ADMIN && hasRole(users, RoleName.USER), null, context, scopeFor(user, users)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HierarchyNodeResponse> children(String email, Long parentId, String role, String status, String search, String startDate, String endDate, String manager) {
        Company company = accessControlService.getCurrentCompany(email);
        User parent = userRepository.findByIdAndCompany(parentId, company).orElseThrow(() -> new BadRequestException("Hierarchy parent not found"));
        List<User> users = filteredUsers(company, role, status, search);
        MetricContext context = metricContext(company, users, parseDate(startDate), parseDate(endDate));
        if (parent.getRole() == RoleName.OWNER) {
            return users.stream()
                    .filter(user -> user.getRole() == RoleName.ADMIN)
                    .filter(user -> !hasText(manager) || contains(user.getFullName(), manager.trim().toLowerCase(Locale.ROOT)))
                    .map(user -> toNode(user, hasRole(users, RoleName.USER), parent.getFullName(), context, scopeFor(user, users)))
                    .toList();
        }
        if (parent.getRole() == RoleName.ADMIN) {
            if (hasText(manager) && !contains(parent.getFullName(), manager.trim().toLowerCase(Locale.ROOT))) {
                return List.of();
            }
            return users.stream()
                    .filter(user -> user.getRole() == RoleName.USER)
                    .map(user -> toNode(user, false, parent.getFullName(), context, scopeFor(user, users)))
                    .toList();
        }
        return List.of();
    }

    private List<User> filteredUsers(Company company, String role, String status, String search) {
        RoleName roleName = parseRole(role);
        Boolean active = parseStatus(status);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .filter(user -> roleName == null || user.getRole() == roleName)
                .filter(user -> active == null || user.isActive() == active)
                .filter(user -> normalizedSearch.isBlank()
                        || contains(user.getFullName(), normalizedSearch)
                        || contains(user.getEmail(), normalizedSearch)
                        || contains(user.getMobileNumber(), normalizedSearch))
                .sorted(Comparator.comparing((User user) -> roleRank(user.getRole())).thenComparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private HierarchyNodeResponse toNode(User user, boolean hasChildren, String reportingManager, MetricContext context, Set<String> owners) {
        return HierarchyNodeResponse.builder()
                .id(user.getId())
                .name(user.getFullName())
                .role(user.getRole().name())
                .department("General")
                .status(user.isActive() ? "Active" : "Inactive")
                .email(user.getEmail())
                .mobile(user.getMobileNumber())
                .reportingManager(reportingManager == null ? "--" : reportingManager)
                .createdAt(user.getCreatedAt())
                .hasChildren(hasChildren)
                .metrics(metricsFor(context, owners))
                .build();
    }

    private Set<String> scopeFor(User user, List<User> users) {
        if (user.getRole() == RoleName.OWNER) {
            return ownerKeys(users);
        }
        if (user.getRole() == RoleName.ADMIN) {
            Set<String> keys = ownerKeys(users.stream().filter(item -> item.getRole() == RoleName.USER || item.getId().equals(user.getId())).toList());
            keys.addAll(keysFor(user));
            return keys;
        }
        return keysFor(user);
    }

    private HierarchyMetricsResponse metricsFor(MetricContext context, Set<String> owners) {
        List<Invoice> invoices = context.invoices().stream().filter(invoice -> belongsTo(invoice.getCreatedBy(), owners)).toList();
        List<Payment> payments = context.payments().stream().filter(payment -> belongsTo(payment.getCreatedBy(), owners)).toList();
        List<Customer> customers = context.customers().stream().filter(customer -> belongsTo(customer.getCreatedBy(), owners)).toList();
        List<Product> products = context.products().stream().filter(product -> belongsTo(product.getCreatedBy(), owners)).toList();
        BigDecimal revenue = invoices.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collection = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = invoices.stream().map(Invoice::getBalanceAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageInvoiceValue = invoices.isEmpty()
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(invoices.size()), 2, java.math.RoundingMode.HALF_UP);
        LocalDateTime lastActivity = latestActivity(invoices, payments, customers, products);
        return HierarchyMetricsResponse.builder()
                .totalCustomers(customers.size())
                .totalInvoices(invoices.size())
                .totalPayments(payments.size())
                .totalProducts(products.size())
                .totalUsers(context.users().stream().filter(user -> belongsTo(user.getEmail(), owners) || belongsTo(user.getFullName(), owners)).count())
                .totalRevenue(revenue)
                .totalCollection(collection)
                .outstandingAmount(outstanding)
                .averageInvoiceValue(averageInvoiceValue)
                .lastActivityDate(lastActivity)
                .build();
    }

    private MetricContext metricContext(Company company, List<User> users, LocalDate startDate, LocalDate endDate) {
        List<Invoice> invoices = invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company).stream()
                .filter(invoice -> inRange(invoice.getInvoiceDate(), startDate, endDate))
                .toList();
        List<Payment> payments = paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company).stream()
                .filter(payment -> inRange(payment.getPaymentDate(), startDate, endDate))
                .toList();
        List<Customer> customers = customerRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .filter(customer -> inRange(customer.getCreatedAt(), startDate, endDate))
                .toList();
        List<Product> products = productRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .filter(product -> inRange(product.getCreatedAt(), startDate, endDate))
                .toList();
        return new MetricContext(users, invoices, payments, customers, products);
    }

    private LocalDateTime latestActivity(List<Invoice> invoices, List<Payment> payments, List<Customer> customers, List<Product> products) {
        return java.util.stream.Stream.of(
                        invoices.stream().map(Invoice::getUpdatedAt),
                        payments.stream().map(Payment::getUpdatedAt),
                        customers.stream().map(Customer::getUpdatedAt),
                        products.stream().map(Product::getUpdatedAt)
                )
                .flatMap(stream -> stream)
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private Set<String> ownerKeys(List<User> users) {
        Set<String> keys = new HashSet<>();
        users.forEach(user -> keys.addAll(keysFor(user)));
        return keys;
    }

    private Set<String> keysFor(User user) {
        Set<String> keys = new HashSet<>();
        if (user.getEmail() != null) {
            keys.add(user.getEmail().trim().toLowerCase(Locale.ROOT));
        }
        if (user.getFullName() != null) {
            keys.add(user.getFullName().trim().toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    private boolean belongsTo(String createdBy, Set<String> owners) {
        return createdBy != null && owners.contains(createdBy.trim().toLowerCase(Locale.ROOT));
    }

    private boolean inRange(LocalDate value, LocalDate startDate, LocalDate endDate) {
        if (value == null) {
            return startDate == null && endDate == null;
        }
        return (startDate == null || !value.isBefore(startDate)) && (endDate == null || !value.isAfter(endDate));
    }

    private boolean inRange(LocalDateTime value, LocalDate startDate, LocalDate endDate) {
        return value != null && inRange(value.toLocalDate(), startDate, endDate);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            throw new BadRequestException("Invalid hierarchy date filter");
        }
    }

    private boolean hasRole(List<User> users, RoleName role) {
        return users.stream().anyMatch(user -> user.getRole() == role);
    }

    private int roleRank(RoleName role) {
        return switch (role) {
            case OWNER -> 0;
            case ADMIN -> 1;
            case USER -> 2;
        };
    }

    private RoleName parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return RoleName.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid hierarchy role filter");
        }
    }

    private Boolean parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        if ("active".equalsIgnoreCase(status)) {
            return true;
        }
        if ("inactive".equalsIgnoreCase(status)) {
            return false;
        }
        return null;
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record MetricContext(List<User> users, List<Invoice> invoices, List<Payment> payments, List<Customer> customers, List<Product> products) {
    }
}
