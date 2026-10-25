package com.billing.entity.enums;

/** Ek login (device) ki halat. ACTIVE ke alawa sab "khatam" hain. */
public enum UserSessionStatus {
    ACTIVE,
    LOGGED_OUT,
    REVOKED,
    EXPIRED
}
