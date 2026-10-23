package com.billing.service.payment;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.PaymentTransaction;
import com.billing.entity.enums.PaymentSource;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ek payment record karne ka common input (manual entry, invoice initial
 * payment, gateway settlement — sab isi se). actorEmail null ho to
 * system actor (webhook / scheduler) ke naam se audit hota hai.
 */
public record PaymentEntry(Company company,
                           String actorEmail,
                           String actorName,
                           Customer customer,
                           Invoice invoice,
                           BigDecimal amount,
                           LocalDate paymentDate,
                           String modeCode,
                           String remarks,
                           String referenceNo,
                           PaymentSource source,
                           PaymentTransaction transaction) {

    public PaymentEntry withInvoiceAndAmount(Invoice nextInvoice, BigDecimal nextAmount) {
        return new PaymentEntry(company, actorEmail, actorName, customer, nextInvoice, nextAmount, paymentDate,
                modeCode, remarks, referenceNo, source, transaction);
    }

    public boolean systemActor() {
        return actorEmail == null;
    }
}
