package com.billing.entity;

import com.billing.entity.enums.DocumentSequenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per financial-year running counter for document numbers (INV/2627/00001).
 * Row is locked (SELECT ... FOR UPDATE) while the next number is issued, so
 * parallel creates never get the same number.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "document_sequences",
        uniqueConstraints = @UniqueConstraint(name = "uk_document_sequences_type_period", columnNames = {"doc_type", "period_key"}))
public class DocumentSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private DocumentSequenceType docType;

    @Column(name = "period_key", nullable = false, length = 10)
    private String periodKey;

    // "last_value" MySQL 8 me reserved word hai, isliye last_number.
    @Column(name = "last_number", nullable = false)
    private long lastNumber;
}
