package com.olp.ai.config.local;

import com.olp.ai.service.SesPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Mock SES — active on local profile only.
 * Logs emails to console instead of sending via AWS SES.
 */
@Service
@Primary
@Profile("local")
@Slf4j
public class MockSesService implements SesPort {

    @Override
    public void sendEmail(String toAddress, String subject, String htmlBody) {
        log.info("[LOCAL MOCK] SES email would be sent:");
        log.info("  To:      {}", toAddress);
        log.info("  Subject: {}", subject);
        log.info("  Body:    {} chars", htmlBody.length());
    }
}
