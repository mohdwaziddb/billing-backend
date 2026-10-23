package com.billing.service;

import com.billing.entity.DocumentSequence;
import com.billing.entity.enums.DocumentSequenceType;
import com.billing.exception.BadRequestException;
import com.billing.repository.DocumentSequenceRepository;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PurchaseRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Issues GST-compliant document numbers: PREFIX/FY/NNNNN, e.g. INV/2627/00001
 * (max 16 chars, unique per financial year, Rule 46(b)).
 */
@Service
@RequiredArgsConstructor
public class DocumentNumberService {

    private final DocumentSequenceRepository documentSequenceRepository;
    private final InvoiceRepository invoiceRepository;
    private final PurchaseRepository purchaseRepository;

    // Caller ki transaction me hi chalta hai: create rollback hua to number bhi
    // wapas, aur row lock commit tak rehta hai (parallel create wait karega).
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentSequenceType type, LocalDate documentDate) {
        if (documentDate == null) {
            throw new BadRequestException("Document date is required");
        }
        String periodKey = financialYearKey(documentDate);
        String prefix = type.getPrefix() + "/" + periodKey + "/";

        DocumentSequence sequence = documentSequenceRepository.lockByDocTypeAndPeriodKey(type, periodKey)
                .orElseGet(() -> {
                    documentSequenceRepository.insertIfAbsent(type.name(), periodKey, highestExistingNumber(type, prefix));
                    return documentSequenceRepository.lockByDocTypeAndPeriodKey(type, periodKey)
                            .orElseThrow(() -> new IllegalStateException("Document sequence row missing for " + type + " " + periodKey));
                });

        long nextNumber = sequence.getLastNumber() + 1;
        sequence.setLastNumber(nextNumber);
        documentSequenceRepository.save(sequence);
        return prefix + String.format("%05d", nextNumber);
    }

    /** Financial year April se shuru: 2026-04-01..2027-03-31 = "2627". */
    static String financialYearKey(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return String.format("%02d%02d", startYear % 100, (startYear + 1) % 100);
    }

    // Sequence row pehli baar banti hai (ya kabhi delete hui) to existing
    // numbers se aage start karo, taaki unique invoice_no/purchase_no na tute.
    private long highestExistingNumber(DocumentSequenceType type, String prefix) {
        List<String> latest = type == DocumentSequenceType.INVOICE
                ? invoiceRepository.findLatestNumbersWithPrefix(prefix, PageRequest.of(0, 1))
                : purchaseRepository.findLatestNumbersWithPrefix(prefix, PageRequest.of(0, 1));
        if (latest.isEmpty()) {
            return 0L;
        }
        Long existing = DataTypeUtility.longValue(latest.get(0).substring(prefix.length()));
        return existing == null ? 0L : existing;
    }
}
