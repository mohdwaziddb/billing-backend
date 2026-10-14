package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.notification.CommunicationHistoryItem;
import com.billing.dto.notification.NotificationChannelType;
import com.billing.dto.notification.NotificationStatus;
import com.billing.entity.Company;
import com.billing.entity.EmailLog;
import com.billing.entity.NotificationLog;
import com.billing.repository.EmailLogRepository;
import com.billing.repository.NotificationLogRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Communication History (Mail/SMS/WhatsApp pages): read-only views over the
 * existing email_logs + notification_logs tables. No new tables, no write
 * changes here — tenant isolation comes from DATABASE-per-tenant (the
 * company parameter follows the existing ignored-company convention).
 */
@Service
@RequiredArgsConstructor
public class CommunicationHistoryService {

    private final AccessControlService accessControlService;
    private final EmailLogRepository emailLogRepository;
    private final NotificationLogRepository notificationLogRepository;

    @Transactional(readOnly = true)
    public PageResponse<CommunicationHistoryItem> mailHistory(Map<String, Object> param, String email) {
        Company company = accessControlService.getCurrentCompany(email);
        HistoryFilter filter = parseFilter(param);
        // Template mails (email_logs) UNION free-form mails (notification_logs EMAIL),
        // merged newest-first. Both queries over-fetch one window so page slicing stays exact.
        int window = (filter.page + 1) * filter.size;
        Pageable windowable = PageRequest.of(0, Math.max(1, window));
        List<CommunicationHistoryItem> merged = new ArrayList<>();
        for (EmailLog log : emailLogRepository.searchHistory(company, filter.status, filter.from, filter.to, filter.search, windowable)) {
            merged.add(CommunicationHistoryItem.builder()
                    .id(log.getId())
                    .channel(NotificationChannelType.EMAIL.name())
                    .source("EMAIL_LOG")
                    .recipient(log.getRecipientEmail())
                    .subject(log.getSubject())
                    .message(log.getEmailBody())
                    .status(log.getStatus())
                    .errorMessage(log.getErrorMessage())
                    .providerName(log.getProviderName())
                    .sender(log.getSenderEmail())
                    .sentBy(log.getSentBy())
                    .sentAt(log.getSentAt())
                    .createdAt(log.getCreatedAt())
                    .build());
        }
        for (NotificationLog log : notificationLogRepository.searchHistory(company,
                NotificationChannelType.EMAIL.name(), filter.status, filter.from, filter.to, filter.search, windowable)) {
            merged.add(fromNotificationLog(log, "NOTIFICATION"));
        }
        long total = emailLogRepository.countHistory(company, filter.status, filter.from, filter.to, filter.search)
                + notificationLogRepository.countHistory(company,
                        NotificationChannelType.EMAIL.name(), filter.status, filter.from, filter.to, filter.search);
        merged.sort(Comparator.comparing(CommunicationHistoryItem::getSentAt, CommunicationHistoryService::compareNewestFirst)
                .thenComparing(CommunicationHistoryItem::getCreatedAt, CommunicationHistoryService::compareNewestFirst)
                .thenComparing(CommunicationHistoryItem::getId, CommunicationHistoryService::compareNewestFirst));
        int from = Math.min(filter.page * filter.size, merged.size());
        int to = Math.min(from + filter.size, merged.size());
        List<CommunicationHistoryItem> records = merged.subList(from, to);
        int totalPages = filter.size == 0 ? 0 : (int) Math.ceil((double) total / filter.size);
        return PageResponse.<CommunicationHistoryItem>builder()
                .records(records)
                .page(filter.page)
                .size(filter.size)
                .totalRecords(total)
                .totalPages(totalPages)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunicationHistoryItem> channelHistory(Map<String, Object> param, String email, NotificationChannelType channel) {
        Company company = accessControlService.getCurrentCompany(email);
        HistoryFilter filter = parseFilter(param);
        Pageable pageable = PageRequest.of(filter.page, filter.size);
        List<CommunicationHistoryItem> records = new ArrayList<>();
        long total = notificationLogRepository.countHistory(company, channel.name(), filter.status, filter.from, filter.to, filter.search);
        for (NotificationLog log : notificationLogRepository.searchHistory(company, channel.name(),
                filter.status, filter.from, filter.to, filter.search, pageable)) {
            records.add(fromNotificationLog(log, "NOTIFICATION"));
        }
        int totalPages = filter.size == 0 ? 0 : (int) Math.ceil((double) total / filter.size);
        return PageResponse.<CommunicationHistoryItem>builder()
                .records(records)
                .page(filter.page)
                .size(filter.size)
                .totalRecords(total)
                .totalPages(totalPages)
                .build();
    }

    private CommunicationHistoryItem fromNotificationLog(NotificationLog log, String source) {
        return CommunicationHistoryItem.builder()
                .id(log.getId())
                .channel(log.getChannel())
                .source(source)
                .recipient(log.getRecipient())
                .subject(log.getSubject())
                .message(log.getMessage())
                .status(log.getStatus())
                .errorMessage(log.getFailureReason())
                .providerName(log.getProviderName())
                .sender(log.getSender())
                .messageId(log.getMessageId())
                .sentBy(log.getSentBy())
                .sentAt(log.getSentAt())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private static <T extends Comparable<T>> int compareNewestFirst(T left, T right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    private HistoryFilter parseFilter(Map<String, Object> param) {
        Map<String, Object> safe = param == null ? Map.of() : param;
        int page = DataTypeUtility.integerValue(safe.get("page"));
        int size = DataTypeUtility.integerValue(safe.get("size"));
        if (size == 0) {
            size = 20;
        }
        String rawStatus = DataTypeUtility.stringValue(safe.get("status")).trim().toUpperCase(java.util.Locale.ROOT);
        String status = null;
        for (NotificationStatus allowed : NotificationStatus.values()) {
            if (allowed.name().equals(rawStatus)) {
                status = allowed.name();
                break;
            }
        }
        String search = DataTypeUtility.stringValue(safe.get("search")).trim();
        if (search.isEmpty()) {
            search = null;
        }
        LocalDate fromDate = parseDate(DataTypeUtility.stringValue(safe.get("from")));
        if (fromDate == null) {
            fromDate = parseDate(DataTypeUtility.stringValue(safe.get("startDate")));
        }
        LocalDate toDate = parseDate(DataTypeUtility.stringValue(safe.get("to")));
        if (toDate == null) {
            toDate = parseDate(DataTypeUtility.stringValue(safe.get("endDate")));
        }
        return new HistoryFilter(
                Math.max(0, page),
                Math.max(1, Math.min(size, 100)),
                status,
                fromDate == null ? null : fromDate.atStartOfDay(),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay(),
                search);
    }

    private LocalDate parseDate(String value) {
        LocalDate parsed = DataTypeUtility.parseLocalDate(value, "yyyy-MM-dd");
        if (parsed == null) {
            parsed = DataTypeUtility.parseLocalDate(value, "dd-MM-yyyy");
        }
        return parsed;
    }

    private record HistoryFilter(int page, int size, String status, LocalDateTime from, LocalDateTime to, String search) {
    }
}
