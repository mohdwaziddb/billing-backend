package com.billing.service;

import com.billing.entity.Company;
import com.billing.entity.TaxMaster;
import com.billing.entity.enums.TaxType;
import com.billing.repository.ProductRepository;
import com.billing.repository.TaxMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Invoice/product tax resolve: duplicate rate par 500 nahi, aur default tax chupchaap nahi badalta. */
@ExtendWith(MockitoExtension.class)
class TaxResolveRulesTest {

    @Mock
    private TaxMasterRepository taxMasterRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AuditNameResolver auditNameResolver;
    @Mock
    private ProductRepository productRepository;

    private final Company company = Company.builder().id(1L).name("Acme").build();
    private final BigDecimal zero = new BigDecimal("0.00");

    private TaxMasterService service() {
        return new TaxMasterService(taxMasterRepository, accessControlService, auditLogService, auditNameResolver,
                new TaxValidator(taxMasterRepository), new TaxMapper(), productRepository);
    }

    private TaxMaster tax(long id, String name, boolean active, boolean defaultTax) {
        return TaxMaster.builder().id(id).taxName(name).taxType(TaxType.GST).rate(zero)
                .active(active).defaultTax(defaultTax).deleted(false).build();
    }

    @Test
    void twoZeroRateTaxesPickActiveOneInsteadOfFailing() {
        // Query khud active-first order karti hai; default method pehli row leta hai.
        when(taxMasterRepository.findByCompanyAndTaxTypeAndRateAndDeletedFalse(company, TaxType.GST, zero)).thenCallRealMethod();
        when(taxMasterRepository.findAllByCompanyAndTaxTypeAndRateAndDeletedFalse(company, TaxType.GST, zero))
                .thenReturn(List.of(tax(7, "Nil Rated 0%", true, false), tax(8, "Exempt 0%", true, false)));

        TaxMaster resolved = service().resolveForProduct(company, null, null, false);

        assertEquals(7L, resolved.getId());
    }

    @Test
    void bootSeedKeepsUserChosenDefaultTax() {
        // Har restart par seed chalta hai: pehle 0% ko zabardasti default bana deta tha.
        when(taxMasterRepository.findByCompanyAndTaxTypeAndRateAndDeletedFalse(eq(company), eq(TaxType.GST), any())).thenCallRealMethod();
        when(taxMasterRepository.findAllByCompanyAndTaxTypeAndRateAndDeletedFalse(eq(company), eq(TaxType.GST), any()))
                .thenAnswer(invocation -> List.of(TaxMaster.builder().id(1L).taxType(TaxType.GST).rate(invocation.getArgument(2))
                        .active(true).defaultTax(false).deleted(false).build()));
        when(taxMasterRepository.findByCompanyAndDefaultTaxTrueAndDeletedFalse(company))
                .thenReturn(java.util.Optional.of(tax(9, "GST 18%", true, true)));

        service().createDefaultTaxesForCompany(company);

        verify(taxMasterRepository, never()).save(any(TaxMaster.class));
    }

    @Test
    void nonTaxableItemDoesNotStealCompanyDefaultTax() {
        when(taxMasterRepository.findByCompanyAndTaxTypeAndRateAndDeletedFalse(eq(company), eq(TaxType.GST), any())).thenCallRealMethod();
        when(taxMasterRepository.findAllByCompanyAndTaxTypeAndRateAndDeletedFalse(eq(company), eq(TaxType.GST), any())).thenReturn(List.of());
        when(taxMasterRepository.save(any(TaxMaster.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().resolveForProduct(company, null, null, false);

        ArgumentCaptor<TaxMaster> saved = ArgumentCaptor.forClass(TaxMaster.class);
        verify(taxMasterRepository).save(saved.capture());
        assertEquals(zero, saved.getValue().getRate());
        assertFalse(saved.getValue().isDefaultTax());
        verify(taxMasterRepository, never()).findByCompanyAndDefaultTaxTrueAndDeletedFalse(any());
    }
}
