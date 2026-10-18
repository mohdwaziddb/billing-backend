package com.billing.core.appconfig;

/**
 * Single source of truth for "provider not configured" messages so every
 * surface (password reset, communication settings tests, notification sends,
 * mail/SMS/WhatsApp history) shows the same clear wording.
 *
 * - CORE: short form used in history rows / provider responses.
 * - USER: end-user facing (adds who to reach for help).
 * - ADMIN: admin facing (points to where the fix lives).
 */
public final class ProviderMessages {

    private ProviderMessages() {
    }

    public static final String EMAIL_NOT_CONFIGURED = "Email provider is not configured.";
    public static final String EMAIL_NOT_CONFIGURED_USER =
            EMAIL_NOT_CONFIGURED + " Please contact your administrator.";
    public static final String EMAIL_NOT_CONFIGURED_ADMIN =
            EMAIL_NOT_CONFIGURED + " Add and activate an email provider in Communication settings.";

    public static final String SMS_NOT_CONFIGURED = "SMS provider is not configured.";
    public static final String SMS_NOT_CONFIGURED_ADMIN =
            SMS_NOT_CONFIGURED + " Add and activate an SMS provider in Communication settings.";

    public static final String WHATSAPP_NOT_CONFIGURED = "WhatsApp provider is not configured.";
    public static final String WHATSAPP_NOT_CONFIGURED_ADMIN =
            WHATSAPP_NOT_CONFIGURED + " Add and activate a WhatsApp provider in Communication settings.";
}
