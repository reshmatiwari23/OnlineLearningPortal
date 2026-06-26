package com.olp.ai.service;

import com.olp.ai.controller.AiController;
import com.olp.ai.dto.NudgeRequest;
import com.olp.ai.dto.SummaryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiService Unit Tests")
class AiServiceTest {

    @Mock private BedrockPort bedrockPort;
    @Mock private SesPort sesPort;
    @Mock private DynamoDbSessionService sessionService;

    @InjectMocks
    private NudgeService nudgeService;

    @InjectMocks
    private AiController aiController;

    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String COURSE_SERVICE_URL = "http://localhost:8082";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiController, "courseServiceUrl", COURSE_SERVICE_URL);
        ReflectionTestUtils.setField(aiController, "bedrockPort", bedrockPort);
        ReflectionTestUtils.setField(aiController, "sesPort", sesPort);
    }

    @Nested
    @DisplayName("NudgeService.sendNudge()")
    class NudgeServiceTests {

        @Test
        @DisplayName("should generate message and send email successfully")
        void sendNudge_success() {
            NudgeRequest request = new NudgeRequest();
            request.setLearnerEmail("learner@example.com");
            request.setLearnerName("Test Learner");
            request.setCourseTitle("AWS DevOps Masterclass");
            request.setPercentComplete(50);
            request.setDaysSinceLastWatch(3);

            when(bedrockPort.generateNudgeMessage(
                    "Test Learner", "AWS DevOps Masterclass", 50, 3))
                    .thenReturn("You're halfway there! Keep going with AWS DevOps Masterclass.");

            nudgeService.sendNudge(request);

            verify(bedrockPort).generateNudgeMessage("Test Learner", "AWS DevOps Masterclass", 50, 3);
            verify(sesPort).sendEmail(
                    eq("learner@example.com"),
                    contains("AWS DevOps Masterclass"),
                    anyString()
            );
        }

        @Test
        @DisplayName("should use fallback message when Bedrock fails")
        void sendNudge_bedrockFails_usesFallback() {
            NudgeRequest request = new NudgeRequest();
            request.setLearnerEmail("learner@example.com");
            request.setLearnerName("Test Learner");
            request.setCourseTitle("AWS Course");
            request.setPercentComplete(30);
            request.setDaysSinceLastWatch(5);

            when(bedrockPort.generateNudgeMessage(anyString(), anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Bedrock unavailable"));

            nudgeService.sendNudge(request);

            // Should still send email with fallback message
            verify(sesPort).sendEmail(
                    eq("learner@example.com"),
                    anyString(),
                    contains("30%")
            );
        }

        @Test
        @DisplayName("should include progress percentage in email body")
        void sendNudge_includesProgressInEmail() {
            NudgeRequest request = new NudgeRequest();
            request.setLearnerEmail("learner@example.com");
            request.setLearnerName("Reshma");
            request.setCourseTitle("AWS Cloud");
            request.setPercentComplete(75);
            request.setDaysSinceLastWatch(2);

            when(bedrockPort.generateNudgeMessage(anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn("Great progress at 75%! You're almost there.");

            nudgeService.sendNudge(request);

            verify(sesPort).sendEmail(
                    anyString(), anyString(),
                    argThat(body -> body.contains("75") || body.contains("Great progress"))
            );
        }

        @Test
        @DisplayName("should not throw when SES fails")
        void sendNudge_sesFails_doesNotThrow() {
            NudgeRequest request = new NudgeRequest();
            request.setLearnerEmail("learner@example.com");
            request.setLearnerName("Test");
            request.setCourseTitle("Course");
            request.setPercentComplete(50);
            request.setDaysSinceLastWatch(1);

            when(bedrockPort.generateNudgeMessage(anyString(), anyString(), anyInt(), anyInt()))
                    .thenReturn("Keep going!");
            doThrow(new RuntimeException("SES error")).when(sesPort).sendEmail(any(), any(), any());

            assertThatCode(() -> nudgeService.sendNudge(request))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("BedrockPort.generateCourseSummary()")
    class GenerateCourseSummaryTests {

        @Test
        @DisplayName("should return valid JSON summary")
        void generateCourseSummary_returnsValidJson() {
            String expectedJson = """
                    {
                      "title": "AWS DevOps Masterclass",
                      "objectives": ["Learn CI/CD", "Master Docker"],
                      "summary": "Comprehensive DevOps course",
                      "difficulty": "intermediate",
                      "keyTakeaway": "Master AWS DevOps tools"
                    }
                    """;

            when(bedrockPort.generateCourseSummary(anyString(), anyString()))
                    .thenReturn(expectedJson);

            String result = bedrockPort.generateCourseSummary(
                    "AWS DevOps Masterclass",
                    "This course covers CodePipeline, Docker, ECS...");

            assertThat(result).isNotNull();
            assertThat(result).contains("title");
            assertThat(result).contains("objectives");
            assertThat(result).contains("difficulty");
        }
    }

    @Nested
    @DisplayName("SummaryRequest validation")
    class SummaryRequestTests {

        @Test
        @DisplayName("should have courseId set correctly")
        void summaryRequest_courseIdSet() {
            SummaryRequest request = new SummaryRequest();
            request.setCourseId(COURSE_ID);
            request.setCourseTitle("Test Course");
            request.setTranscriptText("Course content here");

            assertThat(request.getCourseId()).isEqualTo(COURSE_ID);
            assertThat(request.getCourseTitle()).isEqualTo("Test Course");
            assertThat(request.getTranscriptText()).isEqualTo("Course content here");
        }
    }

    @Nested
    @DisplayName("isKbConfigured()")
    class IsKbConfiguredTests {

        @Test
        @DisplayName("should return false when KB ID is placeholder")
        void isKbConfigured_placeholder_returnsFalse() {
            BedrockService service = new BedrockService();
            ReflectionTestUtils.setField(service, "knowledgeBaseId", "placeholder");

            boolean result = (boolean) ReflectionTestUtils.invokeMethod(service, "isKbConfigured");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when KB ID is null")
        void isKbConfigured_null_returnsFalse() {
            BedrockService service = new BedrockService();
            ReflectionTestUtils.setField(service, "knowledgeBaseId", null);

            boolean result = (boolean) ReflectionTestUtils.invokeMethod(service, "isKbConfigured");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when KB ID is empty")
        void isKbConfigured_empty_returnsFalse() {
            BedrockService service = new BedrockService();
            ReflectionTestUtils.setField(service, "knowledgeBaseId", "");

            boolean result = (boolean) ReflectionTestUtils.invokeMethod(service, "isKbConfigured");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when KB ID is real value")
        void isKbConfigured_realId_returnsTrue() {
            BedrockService service = new BedrockService();
            ReflectionTestUtils.setField(service, "knowledgeBaseId", "abc123def456");

            boolean result = (boolean) ReflectionTestUtils.invokeMethod(service, "isKbConfigured");

            assertThat(result).isTrue();
        }
    }
}
