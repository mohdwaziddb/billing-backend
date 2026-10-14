package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    @Query("select e from EmailLog e where (:company is null or 1=1) order by e.sentAt desc, e.createdAt desc")
    Page<EmailLog> findByCompanyOrderBySentAtDescCreatedAtDesc(@Param("company") Company company, Pageable pageable);

    @Query("select e from EmailLog e where (:company is null or 1=1)"
            + " and (:status is null or e.status = :status)"
            + " and (:fromDate is null or e.createdAt >= :fromDate)"
            + " and (:toDate is null or e.createdAt <= :toDate)"
            + " and (:search is null or lower(e.recipientEmail) like lower(concat('%', :search, '%'))"
            + " or lower(e.subject) like lower(concat('%', :search, '%'))"
            + " or lower(e.emailBody) like lower(concat('%', :search, '%')))"
            + " order by e.sentAt desc, e.createdAt desc")
    Page<EmailLog> searchHistory(@Param("company") Company company,
                                 @Param("status") String status,
                                 @Param("fromDate") java.time.LocalDateTime fromDate,
                                 @Param("toDate") java.time.LocalDateTime toDate,
                                 @Param("search") String search,
                                 Pageable pageable);

    @Query("select count(e) from EmailLog e where (:company is null or 1=1)"
            + " and (:status is null or e.status = :status)"
            + " and (:fromDate is null or e.createdAt >= :fromDate)"
            + " and (:toDate is null or e.createdAt <= :toDate)"
            + " and (:search is null or lower(e.recipientEmail) like lower(concat('%', :search, '%'))"
            + " or lower(e.subject) like lower(concat('%', :search, '%'))"
            + " or lower(e.emailBody) like lower(concat('%', :search, '%')))")
    long countHistory(@Param("company") Company company,
                      @Param("status") String status,
                      @Param("fromDate") java.time.LocalDateTime fromDate,
                      @Param("toDate") java.time.LocalDateTime toDate,
                      @Param("search") String search);
}
