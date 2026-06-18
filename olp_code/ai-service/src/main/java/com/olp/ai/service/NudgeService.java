package com.olp.ai.service;

import com.olp.ai.dto.NudgeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

/**
 * Generates and sends personalised progress nudge emails.
 *
 * Uses Claude 3 Haiku for message generation (20x cheaper than Sonnet).
 * Sends via Amazon SES.
 *
 * Called by a daily EventBridge scheduler (not by the frontend) for learners who:
 *   - Have not watched any video in 3+ days
 *   - Are less than 50% through a course
 *   - Have not completed the course
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NudgeService {

    private final BedrockService bedrockService;
    private final SesClient sesClient;

    private static final String FROM_EMAIL = "learning@olp.example.com";

    /**
     * Generate a personalised nudge message and send it via SES.
     *
     * @param request contains learner details and course progress info
     * @return true if sent successfully, false on error
     */
    public boolean sendNudge(NudgeRequest request) {
        try {
            // Generate the personalised message using Claude Haiku
            String nudgeMessage = bedrockService.generateNudgeMessage(
                    request.getLearnerName(),
                    request.getCourseTitle(),
                    request.getPercentComplete(),
                    request.getDaysSinceLastWatch()
            );

            log.info("Generated nudge for learner: {}, course: {}",
                    request.getLearnerEmail(), request.getCourseTitle());

            // Build the email
            String emailBody = buildEmailBody(
                    request.getLearnerName(),
                    request.getCourseTitle(),
                    request.getPercentComplete(),
                    nudgeMessage
            );

            // Send via Amazon SES
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .source(FROM_EMAIL)
                    .destination(Destination.builder()
                            .toAddresses(request.getLearnerEmail())
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data("Continue your learning journey — " + request.getCourseTitle())
                                    .charset("UTF-8")
                                    .build())
                            .body(Body.builder()
                                    .html(Content.builder()
                                            .data(emailBody)
                                            .charset("UTF-8")
                                            .build())
                                    .build())
                            .build())
                    .build();

            sesClient.sendEmail(emailRequest);
            log.info("Nudge email sent to: {}", request.getLearnerEmail());
            return true;

        } catch (Exception e) {
            log.error("Failed to send nudge to {}: {}", request.getLearnerEmail(), e.getMessage());
            return false;
        }
    }

    private String buildEmailBody(String name, String courseTitle,
                                   int percent, String aiMessage) {
        return String.format("""
                <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                  <h2 style="color: #1F3864;">Hi %s 👋</h2>
                  <p>%s</p>
                  <p style="color: #666;">You are <strong>%d%%</strong> through
                     <em>%s</em>.</p>
                  <a href="https://olp.example.com/courses"
                     style="background: #05819B; color: white; padding: 12px 24px;
                            text-decoration: none; border-radius: 6px; display: inline-block;">
                    Continue Learning
                  </a>
                  <p style="color: #999; font-size: 12px; margin-top: 24px;">
                    Online Learning Portal
                  </p>
                </body></html>
                """, name, aiMessage, percent, courseTitle);
    }
}
