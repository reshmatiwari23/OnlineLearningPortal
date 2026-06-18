package com.olp.ai.service;

import com.olp.ai.dto.NudgeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Generates and sends personalised progress nudge emails.
 * Uses BedrockPort (Claude Haiku) for message generation.
 * Uses SesPort for email delivery.
 * Both are injected as interfaces — works with real and mock implementations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NudgeService {

    private final BedrockPort bedrockPort;
    private final SesPort sesPort;

    public boolean sendNudge(NudgeRequest request) {
        try {
            // Generate personalised message via Claude Haiku (20x cheaper than Sonnet)
            String nudgeMessage = bedrockPort.generateNudgeMessage(
                    request.getLearnerName(),
                    request.getCourseTitle(),
                    request.getPercentComplete(),
                    request.getDaysSinceLastWatch()
            );

            String subject = "Continue your learning journey — " + request.getCourseTitle();
            String body = buildEmailBody(
                    request.getLearnerName(),
                    request.getCourseTitle(),
                    request.getPercentComplete(),
                    nudgeMessage
            );

            sesPort.sendEmail(request.getLearnerEmail(), subject, body);
            log.info("Nudge sent to: {}", request.getLearnerEmail());
            return true;

        } catch (Exception e) {
            log.error("Nudge failed for {}: {}", request.getLearnerEmail(), e.getMessage());
            return false;
        }
    }

    private String buildEmailBody(String name, String courseTitle,
                                   int percent, String aiMessage) {
        return String.format("""
                <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto">
                  <h2 style="color:#1F3864">Hi %s 👋</h2>
                  <p>%s</p>
                  <p style="color:#666">You are <strong>%d%%</strong> through <em>%s</em>.</p>
                  <a href="https://olp.example.com/courses"
                     style="background:#05819B;color:white;padding:12px 24px;
                            text-decoration:none;border-radius:6px;display:inline-block">
                    Continue Learning
                  </a>
                </body></html>
                """, name, aiMessage, percent, courseTitle);
    }
}
