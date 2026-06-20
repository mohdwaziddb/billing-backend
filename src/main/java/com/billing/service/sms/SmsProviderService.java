package com.billing.service.sms;

import com.billing.entity.Company;
import com.billing.entity.SmsProviderSetting;

import java.util.List;

public interface SmsProviderService {

    String providerType();

    List<SmsSendResult> sendSms(Company company, SmsProviderSetting settings, List<String> mobileNumbers, String message);

    SmsSendResult sendOtp(Company company, SmsProviderSetting settings, String mobileNumber, String message);

    SmsSendResult testConnection(Company company, SmsProviderSetting settings, String mobileNumber, String message);
}
