package com.olp.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

/**
 * Real SES implementation — active on aws profile only.
 * Implements SesPort so NudgeService can inject without AWS SDK dependency.
 */
@Service
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class SesService implements SesPort {

    private final SesClient sesClient;

    private static final String FROM_EMAIL = "reshma.tiwari9@gmail.com";

    @Override
    public void sendEmail(String toAddress, String subject, String htmlBody) {
        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                    .source(FROM_EMAIL)
                    .destination(Destination.builder().toAddresses(toAddress).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build());
            log.info("SES email sent to: {}", toAddress);
        } catch (Exception e) {
            log.error("SES send failed to {}: {}", toAddress, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
