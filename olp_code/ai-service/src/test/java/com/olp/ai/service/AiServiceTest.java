package com.olp.ai.service;

import com.olp.ai.dto.NudgeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NudgeService Unit Tests")
class AiServiceTest {

    @Mock private BedrockPort bedrockPort;
    @Mock private SesPort sesPort;

    @InjectMocks
    private NudgeService nudgeService;

    private NudgeRequest nudgeRequest;

    @BeforeEach
    void setUp() {
        nudgeRequest = new NudgeRequest();
        nudgeRequest.setLearnerEmail("learner@example.com");
        nudgeRequest.setLearnerName("Reshma");
        nudgeRequest.setCourseTitle("AWS DevOps Masterclass");
        nudgeRequest.setPercentComplete(50);
        nudgeRequest.setDaysSinceLastWatch(3);
    }

    @Nested
    @DisplayName("sendNudge()")
    class SendNudgeTests {

        @Test
        @DisplayName("should generate message and send email — returns true")
        void sendNudge_success_returnsTrue() {
            when(bedrockPort.generateNudgeMessage(
                    "Reshma", "AWS DevOps Masterclass", 50, 3))
                    .thenReturn("You're halfway there! Keep going.");

            boolean result = nudgeService.sendNudge(nudgeRequest);

            assertThat(result).isTrue();
            verify(bedrockPort).generateNudgeMessage("Reshma", "AWS DevOps Masterclass", 50, 3);
            verify(sesPort).sendEmail(
                    eq("learner@example.com"),
                    contains("AWS DevOps Masterclass"),
                    anyString()
            );
        }

        @Test
        @DisplayName("should return false when Bedrock throws exception")
        void sendNudge_bedrockFails_returnsFalse() {
            when(bedrockPort.generateNudgeMessage(anyString(), anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Bedrock unavailable"));

            boolean result = nudgeService.sendNudge(nudgeRequest);

            assertThat(result).isFalse();
            verify(sesPort, never()).sendEmail(any(), any(), any());
        }

        @Test
        @DisplayName("should return false when SES throws exception")
        void sendNudge_sesFails_returnsFalse() {
            when(bedrockPort.generateNudgeMessage(anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn("Keep going!");
            doThrow(new RuntimeException("SES error"))
                    .when(sesPort).sendEmail(any(), any(), any());

            boolean result = nudgeService.sendNudge(nudgeRequest);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should include progress percentage in email body")
        void sendNudge_includesProgressInBody() {
            when(bedrockPort.generateNudgeMessage(anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn("Great progress!");

            nudgeService.sendNudge(nudgeRequest);

            verify(sesPort).sendEmail(
                    anyString(), anyString(),
                    argThat(body -> body.contains("50%") || body.contains("50"))
            );
        }

        @Test
        @DisplayName("should include learner name in email body")
        void sendNudge_includesLearnerName() {
            when(bedrockPort.generateNudgeMessage(anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn("Hi!");

            nudgeService.sendNudge(nudgeRequest);

            verify(sesPort).sendEmail(
                    anyString(), anyString(),
                    argThat(body -> body.contains("Reshma"))
            );
        }

        @Test
        @DisplayName("should send to correct email address")
        void sendNudge_sendsToCorrectEmail() {
            when(bedrockPort.generateNudgeMessage(anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn("Keep learning!");

            nudgeService.sendNudge(nudgeRequest);

            verify(sesPort).sendEmail(
                    eq("learner@example.com"),
                    anyString(),
                    anyString()
            );
        }

        @Test
        @DisplayName("should use correct parameters for 100% complete")
        void sendNudge_completed_sendsMessage() {
            nudgeRequest.setPercentComplete(100);
            nudgeRequest.setDaysSinceLastWatch(0);

            when(bedrockPort.generateNudgeMessage("Reshma", "AWS DevOps Masterclass", 100, 0))
                    .thenReturn("Congratulations on completing the course!");

            boolean result = nudgeService.sendNudge(nudgeRequest);

            assertThat(result).isTrue();
            verify(bedrockPort).generateNudgeMessage("Reshma", "AWS DevOps Masterclass", 100, 0);
        }
    }
}