package com.billing.repository;

import com.billing.entity.DocumentSequence;
import com.billing.entity.enums.DocumentSequenceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.docType = :docType and s.periodKey = :periodKey")
    Optional<DocumentSequence> lockByDocTypeAndPeriodKey(@Param("docType") DocumentSequenceType docType,
                                                         @Param("periodKey") String periodKey);

    // INSERT IGNORE: do parallel requests ek saath pehla row banayein to
    // duplicate-key exception (jo poori transaction rollback-only kar deta)
    // ki jagah doosra insert chup-chaap skip ho jata hai.
    @Modifying
    @Query(value = "insert ignore into document_sequences (doc_type, period_key, last_number) values (:docType, :periodKey, :lastNumber)",
            nativeQuery = true)
    int insertIfAbsent(@Param("docType") String docType,
                       @Param("periodKey") String periodKey,
                       @Param("lastNumber") long lastNumber);
}
