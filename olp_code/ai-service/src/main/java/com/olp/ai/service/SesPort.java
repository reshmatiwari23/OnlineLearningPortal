package com.olp.ai.service;

/**
 * Interface for SES email sending.
 * SesService implements with real AWS SDK (aws profile).
 * MockSesService implements by logging (local profile).
 */
public interface SesPort {
    void sendEmail(String toAddress, String subject, String htmlBody);
}
