package com.billing.repository;

import com.billing.entity.Invoice;
import com.billing.entity.PaymentTransaction;
import com.billing.entity.enums.PaymentTransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByTxnRef(String txnRef);

    // Settle hamesha row lock ke saath: webhook + verify + reconcile ek saath aayein to bhi ek hi Payment bane.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.txnRef = :txnRef")
    Optional<PaymentTransaction> lockByTxnRef(@Param("txnRef") String txnRef);

    Optional<PaymentTransaction> findFirstByGatewayOrderId(String gatewayOrderId);

    Optional<PaymentTransaction> findFirstByGatewayLinkId(String gatewayLinkId);

    List<PaymentTransaction> findByInvoiceOrderByIdDesc(Invoice invoice);

    List<PaymentTransaction> findByStatusInAndCreatedAtBetweenOrderByIdAsc(Collection<PaymentTransactionStatus> statuses,
                                                                         LocalDateTime from,
                                                                         LocalDateTime to);

    boolean existsByInvoiceAndStatusIn(Invoice invoice, Collection<PaymentTransactionStatus> statuses);
}
