# Online Learning Portal — AI Generation Prompts
## Master prompt log — save this file, update it as you add each service

---

## How to use this file

Each section below contains:
1. The exact prompt used to generate the code
2. The files it generated
3. How to modify and regenerate

To regenerate any file, copy the prompt into a new chat with Claude and paste it.
To modify a file, copy the prompt, change the relevant part, and regenerate.

---

## Project Context (used in ALL prompts)

```
Project:    Online Learning Portal
Stack:      Java 17, Spring Boot 3.2.0, Maven multi-module
Database:   RDS PostgreSQL 15 (AWS ap-south-1)
Cache:      ElastiCache Redis 7.x (TLS + AUTH)
Auth:       Amazon Cognito User Pool + JWT RS256
Storage:    Amazon S3 (olp-videos + olp-assets)
CDN:        Amazon CloudFront (OAC for videos, WAF with 3 managed rules)
AI:         AWS Bedrock (Claude 3 Sonnet, Claude 3 Haiku, Titan Embeddings v2)
Container:  ECS Fargate (deployed via Docker, ECR)
CI/CD:      AWS CodeBuild → ECR → ECS
Queue:      Amazon SQS (progress writes + video processing)
Serverless: AWS Lambda (video processor, S3 trigger)
Email:      Amazon SES (nudge emails)
NoSQL:      Amazon DynamoDB (AI sessions, TTL 24h)
Region:     ap-south-1 (Mumbai)
Bedrock Region: ap-south-1 (active models: claude-3-sonnet, claude-3-haiku)
```

---

## PROMPT 001 — Parent POM (pom.xml)

**Files generated:** `online-learning-portal/pom.xml`

**Prompt:**
```
Generate a Maven parent POM for a multi-module Spring Boot 3.2.0 project called
online-learning-portal using Java 17.

Modules:
- common
- auth-service
- course-service
- enrollment-service
- progress-service
- ai-service

Dependency versions to lock (dependencyManagement):
- spring-boot: 3.2.0
- spring-cloud-aws: 3.1.0
- postgresql driver: 42.7.1
- jjwt (io.jsonwebtoken): 0.12.3
- lombok: 1.18.30
- mapstruct: 1.5.5.Final
- testcontainers: 1.19.3
- aws-sdk-java-v2 (BOM): 2.21.0
- flyway: 10.10.0 (IMPORTANT: use 10.x not 9.x — required for Spring Boot 3.2)

Plugin versions:
- spring-boot-maven-plugin: 3.2.0
- maven-compiler-plugin: 3.11.0

Java version: 17
Packaging: pom (parent)
GroupId: com.olp

IMPORTANT: Use Flyway 10.10.0 — NOT 9.22.3. Spring Boot 3.2 requires Flyway 10.x.
```

---

## PROMPT 002 — Common Module POM

**Files generated:** `common/pom.xml`

**Prompt:**
```
Generate a Maven POM for the 'common' module of the online-learning-portal
multi-module project.

Parent: com.olp:online-learning-portal:1.0.0
ArtifactId: common
Packaging: jar

Dependencies needed:
- spring-boot-starter (no web — just core)
- spring-boot-starter-security
- jjwt-api, jjwt-impl, jjwt-jackson (io.jsonwebtoken 0.12.3)
- lombok
- jakarta.validation-api

No Spring Boot plugin needed (not a runnable app, just a library).
```

---

## PROMPT 003 — Common Module Java Classes

**Files generated:**
- `common/src/main/java/com/olp/common/dto/ApiResponse.java`
- `common/src/main/java/com/olp/common/exception/ResourceNotFoundException.java`
- `common/src/main/java/com/olp/common/exception/DuplicateResourceException.java`
- `common/src/main/java/com/olp/common/exception/UnauthorisedException.java`
- `common/src/main/java/com/olp/common/util/JwtUtil.java`

**Prompt:**
```
Generate these Java classes for the 'common' module of a Spring Boot 3.2 project.
Package: com.olp.common
Use Java 17, Lombok, JJWT 0.12.3.

1. ApiResponse<T> — generic response wrapper
   Fields: boolean success, T data, String message, LocalDateTime timestamp
   Static factory methods: success(T data), success(T data, String message),
   error(String message)

2. ResourceNotFoundException extends RuntimeException
   Constructor: (String resourceName, String fieldName, Object fieldValue)
   Message format: "{resourceName} not found with {fieldName}: {fieldValue}"

3. DuplicateResourceException extends RuntimeException
   Constructor: (String message)

4. UnauthorisedException extends RuntimeException
   Constructor: (String message)

5. JwtUtil — utility class for JWT operations using JJWT 0.12.3
   - Method: extractUserId(String token) returns String
   - Method: extractRole(String token) returns String
   - Method: extractAllClaims(String token) returns Claims
   - Method: isTokenValid(String token) returns boolean
   - Reads the Cognito RS256 public key from application properties
     (cognito.jwks-uri) to verify signatures
   - Uses @Value to inject cognito.region and cognito.user-pool-id
```

---

## PROMPT 004 — auth-service POM

**Files generated:** `auth-service/pom.xml`

**Prompt:**
```
Generate a Maven POM for the 'auth-service' module of the online-learning-portal
multi-module project.

Parent: com.olp:online-learning-portal:1.0.0
ArtifactId: auth-service
Packaging: jar

Dependencies needed:
- com.olp:common (project dependency)
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-security
- spring-boot-starter-validation
- spring-boot-starter-actuator
- postgresql driver
- flyway-core
- flyway-database-postgresql
- lombok
- software.amazon.awssdk:cognitoidentityprovider
- software.amazon.awssdk:secretsmanager
- spring-boot-starter-test (test scope)
- testcontainers:postgresql (test scope)

Include spring-boot-maven-plugin so it builds a runnable fat JAR.
Main class: com.olp.auth.AuthServiceApplication

IMPORTANT: flyway version must be 10.10.0 from parent POM.
```

---

## PROMPT 005 — auth-service application.yml

**Files generated:** `auth-service/src/main/resources/application.yml`

**Prompt:**
```
Generate application.yml for auth-service Spring Boot 3.2.

Server:
  port: 8081

Spring datasource:
  url: jdbc:postgresql://${DB_HOST:localhost}:5432/olp_db
  username: ${DB_USERNAME:olp_admin}
  password: ${DB_PASSWORD:localpassword}
  driver: org.postgresql.Driver

HikariCP connection pool:
  maximum-pool-size: 10
  minimum-idle: 2
  connection-timeout: 30000
  idle-timeout: 600000

JPA:
  hibernate ddl-auto: validate (Flyway manages schema)
  show-sql: false
  dialect: PostgreSQL

Flyway:
  enabled: true
  locations: classpath:db/migration
  baseline-on-migrate: true

Cognito:
  region: ${COGNITO_REGION:ap-south-1}
  user-pool-id: ${COGNITO_USER_POOL_ID:ap-south-1_placeholder}
  client-id: ${COGNITO_CLIENT_ID:placeholder}
  jwks-uri: https://cognito-idp.${cognito.region}.amazonaws.com/${cognito.user-pool-id}/.well-known/jwks.json

IMPORTANT: Cognito app client must be created WITHOUT a client secret.
Use USER_PASSWORD_AUTH flow. The client-id used is the one without secret.
Token expiry extended to 24 hours for demo via:
  aws cognito-idp update-user-pool-client
    --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH ALLOW_USER_SRP_AUTH
    --access-token-validity 24 --id-token-validity 24
    --token-validity-units AccessToken=hours,IdToken=hours

Security:
  bcrypt-strength: 12

Logging:
  level root: INFO
  level com.olp: DEBUG

Actuator:
  expose: health, info, metrics
  health show-details: always
```

---

## PROMPT 006 — Flyway migration V1

**Files generated:** `auth-service/src/main/resources/db/migration/V1__init.sql`

**Prompt:**
```
Generate a Flyway V1 SQL migration script for PostgreSQL 15
to create the users table for the Online Learning Portal auth-service.

Table: users
Columns:
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid()
  email         VARCHAR(255) NOT NULL UNIQUE
  password_hash VARCHAR(255) NOT NULL
  name          VARCHAR(255) NOT NULL
  role          VARCHAR(50) NOT NULL DEFAULT 'user'
                CHECK (role IN ('user', 'instructor', 'admin'))
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()

Indexes:
  idx_users_email (unique index on email)
  idx_users_role (index on role)

Also create a trigger to auto-update updated_at on every UPDATE.
```

---

## PROMPT 007 — auth-service Entity, DTOs, Repository

**Files generated:**
- `auth-service/src/main/java/com/olp/auth/entity/User.java`
- `auth-service/src/main/java/com/olp/auth/dto/SignupRequest.java`
- `auth-service/src/main/java/com/olp/auth/dto/LoginRequest.java`
- `auth-service/src/main/java/com/olp/auth/dto/AuthResponse.java`
- `auth-service/src/main/java/com/olp/auth/repository/UserRepository.java`

**Prompt:**
```
Generate these Java classes for auth-service in the Online Learning Portal.
Package: com.olp.auth
Java 17, Spring Boot 3.2, Lombok, Jakarta Validation.

1. User.java — JPA entity
   - @Entity @Table(name = "users")
   - Fields: UUID id, String email, String passwordHash, String name,
             String role, LocalDateTime createdAt, LocalDateTime updatedAt
   - @PrePersist sets createdAt and updatedAt
   - @PreUpdate sets updatedAt
   - Use @Column(name = "password_hash") etc for snake_case mapping
   - Lombok: @Data @NoArgsConstructor @AllArgsConstructor @Builder

2. SignupRequest.java — request DTO
   - Fields: String email, String password, String name, String role
   - Validations: @NotBlank, @Email on email; @NotBlank @Size(min=8) on
     password; @NotBlank on name
   - role defaults to "user" if not provided, validated with @Pattern
     to accept only: user, instructor

3. LoginRequest.java — request DTO
   - Fields: String email, String password
   - Both @NotBlank

4. AuthResponse.java — response DTO
   - Fields: String token, String userId, String email,
             String name, String role, long expiresIn
   - Lombok: @Data @Builder @AllArgsConstructor @NoArgsConstructor

5. UserRepository.java — Spring Data JPA repository
   - extends JpaRepository<User, UUID>
   - Method: Optional<User> findByEmail(String email)
   - Method: boolean existsByEmail(String email)
```

---

## PROMPT 008 — auth-service Service Layer

**Files generated:**
- `auth-service/src/main/java/com/olp/auth/service/AuthService.java`
- `auth-service/src/main/java/com/olp/auth/service/CognitoService.java`

**Prompt:**
```
Generate the service layer for auth-service in the Online Learning Portal.
Java 17, Spring Boot 3.2, AWS SDK v2 for Cognito.

1. CognitoService.java
   - @Service
   - Inject CognitoIdentityProviderClient (AWS SDK v2)
   - Inject @Value cognito.user-pool-id and cognito.client-id
   - Method: createUser(String email, String name, String role)
     → calls adminCreateUser on the User Pool
     → sets email_verified attribute to true
     → sets custom:role attribute to the role value
     → returns the Cognito username (sub)
   - Method: authenticateUser(String email, String password)
     → calls initiateAuth with USER_PASSWORD_AUTH flow
     → IMPORTANT: this flow requires the Cognito app client to have
       NO client secret and explicit auth flow ALLOW_USER_PASSWORD_AUTH
     → returns the IdToken (JWT) from AuthenticationResult
   - Method: deleteUser(String email) — for rollback on signup failure
   - Handle CognitoIdentityProviderException and rethrow as
     DuplicateResourceException or UnauthorisedException from common module

2. AuthService.java
   - @Service @Transactional
   - Inject: UserRepository, PasswordEncoder, CognitoService
   - Method: signup(SignupRequest request) returns AuthResponse
     Step 1: check existsByEmail — throw DuplicateResourceException if exists
     Step 2: hash password with BCrypt
     Step 3: save User to RDS (UserRepository.save)
     Step 4: createUser in Cognito — on failure, delete from RDS (rollback)
     Step 5: authenticateUser in Cognito to get JWT
     Step 6: return AuthResponse with token, userId, email, name, role
   - Method: login(LoginRequest request) returns AuthResponse
     Step 1: find user by email — throw ResourceNotFoundException if not found
     Step 2: verify BCrypt password — throw UnauthorisedException if wrong
     Step 3: authenticateUser in Cognito to get fresh JWT
     Step 4: return AuthResponse
```

---

## PROMPT 009 — auth-service Controller and Security Config

**Files generated:**
- `auth-service/src/main/java/com/olp/auth/controller/AuthController.java`
- `auth-service/src/main/java/com/olp/auth/config/SecurityConfig.java`
- `auth-service/src/main/java/com/olp/auth/config/AwsConfig.java`
- `auth-service/src/main/java/com/olp/auth/exception/GlobalExceptionHandler.java`
- `auth-service/src/main/java/com/olp/auth/AuthServiceApplication.java`

**Prompt:**
```
Generate the controller, config, and main class for auth-service.
Java 17, Spring Boot 3.2, Spring Security 6.

1. AuthController.java
   - @RestController @RequestMapping("/api/auth")
   - POST /signup — accepts @Valid SignupRequest, returns ApiResponse<AuthResponse>
     HTTP 201 on success
   - POST /login  — accepts @Valid LoginRequest, returns ApiResponse<AuthResponse>
     HTTP 200 on success
   - Both endpoints are PUBLIC (no JWT required)

2. SecurityConfig.java
   - @Configuration @EnableWebSecurity
   - disable CSRF, sessionManagement: STATELESS
   - permit: /api/auth/**, /actuator/health, /actuator/info
   - PasswordEncoder bean: BCryptPasswordEncoder(12)
   - Do NOT configure JWT filter — JWT validation is done at ALB level

3. AwsConfig.java
   - @Configuration
   - CognitoIdentityProviderClient bean using DefaultCredentialsProvider
   - IAM role on ECS handles credentials automatically

4. GlobalExceptionHandler.java
   - @RestControllerAdvice
   - Handle ResourceNotFoundException     → 404
   - Handle DuplicateResourceException    → 409
   - Handle UnauthorisedException         → 401
   - Handle MethodArgumentNotValidException → 400
   - Handle generic Exception             → 500

5. AuthServiceApplication.java
   - @SpringBootApplication main method

IMPORTANT: auth-service task role needs AmazonCognitoPowerUser IAM policy.
NAT Gateway is required for ECS tasks to reach Cognito endpoints
(Cognito does not have a VPC endpoint).
```

---

## PROMPT 010 — auth-service Tests

**Files generated:**
- `auth-service/src/test/java/com/olp/auth/AuthControllerTest.java`

**Prompt:** (Same as original)

---

## PROMPT 011 — course-service POM

**Files generated:** `course-service/pom.xml`

**Prompt:** (Same as original, use Flyway 10.10.0)

---

## PROMPT 012 — course-service Entity, DTOs, Repository

**Files generated:** (Same as original)

---

## PROMPT 013 — course-service Service and Controller

**Files generated:**
- `course-service/src/main/java/com/olp/course/service/S3Port.java` (interface)
- `course-service/src/main/java/com/olp/course/service/S3Service.java` (aws profile)
- `course-service/src/main/java/com/olp/course/service/CourseService.java`
- `course-service/src/main/java/com/olp/course/controller/CourseController.java`

**Prompt:**
```
Generate service and controller for course-service.
Java 17, Spring Boot 3.2, AWS SDK v2 S3.

CourseService must handle:
1. CRUD operations with ownership checks (instructor can only modify own courses)
2. S3 presigned URL generation — sets course.videoUrl = s3Key, status = PENDING
3. Upload status update — when READY, convert S3 key to CloudFront URL:
   if (currentUrl != null && !currentUrl.startsWith("http")) {
     course.setVideoUrl("https://" + cloudfrontDomain + "/" + currentUrl);
   }
4. AI summary save — PUT /api/courses/{id} accepts aiSummary field
5. Internal endpoint — PATCH /api/courses/{id}/ai-summary
   - Header: x-internal-service: ai-service (bypass ownership check)
   - Updates aiSummary and kbIngested fields only

CourseController endpoints:
  GET  /api/courses                     — paginated published courses
  GET  /api/courses/my                  — instructor's courses
  GET  /api/courses/search?keyword      — search
  GET  /api/courses/{id}               — single course
  POST /api/courses                     — create (instructor only)
  PUT  /api/courses/{id}               — update (owner only, accepts aiSummary)
  DELETE /api/courses/{id}             — delete (owner only)
  POST /api/courses/{id}/upload-url    — get S3 presigned URL
  PATCH /api/courses/{id}/upload-status — update status (called by Lambda)
  PATCH /api/courses/{id}/ai-summary   — internal AI summary save

Environment variables:
  CLOUDFRONT_DOMAIN: d1ka6o9mjvg4i9.cloudfront.net
  S3_VIDEOS_BUCKET: olp-videos-418272762620

IMPORTANT: The presigned URL upload uses XMLHttpRequest in frontend (not axios)
to avoid header conflicts with S3 presigned URLs.
```

---

## PROMPT 014 — enrollment-service (complete)

**Files generated:** (Same as original)

---

## PROMPT 015 — upload_status column + UploadStatus enum

**Files generated:** (Same as original)

**IMPORTANT ADDITION — V6 migration fix:**

```sql
-- V6__fix_upload_status_constraint.sql
-- Fix: Java enum values are uppercase (READY, PENDING) but constraint was lowercase
ALTER TABLE courses DROP CONSTRAINT IF EXISTS courses_upload_status_check;
ALTER TABLE courses ADD CONSTRAINT courses_upload_status_check
  CHECK (upload_status IN ('NONE','PENDING','PROCESSING','READY','FAILED',
                           'none','pending','processing','ready','failed'));
```

This migration was required because the Java UploadStatus enum uses uppercase
values but the original SQL constraint only accepted lowercase.

---

## PROMPT 016 — progress-service (complete)

**Files generated:** (Same as original, plus additions below)

**CRITICAL ADDITIONS not in original prompt:**

```
1. SqsProgressConsumer.java — MISSING from original design, must be added
   Location: src/aws/java/com/olp/progress/service/SqsProgressConsumer.java
   
   @Service @Profile("!local") @RequiredArgsConstructor @Slf4j
   Uses @Scheduled(fixedDelay = 5000) — polls SQS every 5 seconds
   Receives ProgressEvent messages from olp-progress-writes-queue
   Calls progressService.persistProgressEvent(event) to upsert to RDS
   Deletes message from queue after successful processing
   
2. ProgressServiceApplication.java — must add @EnableScheduling annotation
   Without this, the @Scheduled consumer will not run.

3. Redis security group fix:
   olp-redis-sg must allow inbound TCP 6379 from olp-ecs-sg
   (by default this rule was missing)

4. SQS permissions:
   olp-progress-task-role needs AmazonSQSFullAccess policy
```

**Consumer implementation:**
```java
@Scheduled(fixedDelay = 5000)
public void pollQueue() {
    ReceiveMessageRequest request = ReceiveMessageRequest.builder()
        .queueUrl(queueUrl)
        .maxNumberOfMessages(10)
        .waitTimeSeconds(2)        // long polling
        .visibilityTimeout(30)
        .build();
    
    List<Message> messages = sqsClient.receiveMessage(request).messages();
    for (Message message : messages) {
        ProgressEvent event = objectMapper.readValue(message.body(), ProgressEvent.class);
        progressService.persistProgressEvent(event);
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(message.receiptHandle())
            .build());
    }
}
```

---

## PROMPT 017 — ai-service (complete)

**Files generated:** (Same as original, with critical changes below)

**CRITICAL CHANGES from original design:**

```
1. Bedrock region: us-east-1 → ap-south-1
   The Claude 3 Sonnet and Haiku models are ACTIVE in ap-south-1.
   In us-east-1 they show as LEGACY (blocked).
   
   application.yml:
     bedrock:
       region: ${BEDROCK_REGION:ap-south-1}
       models:
         sonnet: ${BEDROCK_MODEL_SONNET:anthropic.claude-3-sonnet-20240229-v1:0}
         haiku: ${BEDROCK_MODEL_HAIKU:anthropic.claude-3-haiku-20240307-v1:0}

2. BedrockService.invokeWithRAG() — skip KB when not configured:
   private boolean isKbConfigured() {
     return knowledgeBaseId != null
       && !knowledgeBaseId.isEmpty()
       && !knowledgeBaseId.equals("placeholder");
   }
   When KB not configured: use Claude directly as AWS expert assistant.
   System prompt: "You are an expert AWS and cloud computing course assistant..."
   This allows chat to work without expensive OpenSearch Serverless.

3. Use synchronous invokeModel() NOT streaming:
   InvokeModelResponse response = bedrockClient.invokeModel(request);
   Then stream the response word by word via SSE SseEmitter.
   (The streaming Bedrock API had issues with the SDK version used)

4. AiController.generateSummary() — auto-save to course-service:
   After generating summary, call PATCH /api/courses/{courseId}/ai-summary
   with header x-internal-service: ai-service to bypass ownership check.
   
5. NudgeService — SES requires:
   - FROM email must be verified in SES
   - TO email must be verified in SES sandbox mode
   - olp-ai-task-role needs AmazonSESFullAccess policy
   - FROM_EMAIL = "reshma.tiwari9@gmail.com" (verified identity)

6. DynamoDB olp-ai-sessions table:
   - Must be created separately (not auto-created)
   - PAY_PER_REQUEST billing
   - TTL on 'ttl' attribute (enabled)
   - GSI: userId-index on userId field
   - olp-ai-task-role needs AmazonDynamoDBFullAccess policy
```

---

## PROMPT 018 — React 18 Frontend (complete)

**Files generated:** (Same as original, with critical changes below)

**CRITICAL CHANGES from original:**

```
1. aiService.ts — must add x-user-id and x-user-role headers:
   The /ai/chat endpoint requires these headers from localStorage.
   
   const userStr = localStorage.getItem('olp_user');
   let userId = '', userRole = 'user';
   if (userStr) {
     const u = JSON.parse(userStr);
     userId = u.userId ?? '';
     userRole = u.role ?? 'user';
   }
   
   fetch('/ai/chat', {
     headers: {
       'Content-Type': 'application/json',
       'Authorization': `Bearer ${token}`,
       'Accept': 'text/event-stream',
       'x-user-id': userId,      // ← REQUIRED
       'x-user-role': userRole,  // ← REQUIRED
     }
   })

2. aiService.ts — fix SSE parsing to handle event: prefix:
   Track lastEvent variable, process data based on event type:
   event:token → onToken(data + ' ')
   event:citations → onCitations(JSON.parse(data))
   event:error → onError(message)

3. courseService.ts — uploadVideoToS3 must use XMLHttpRequest not axios:
   Axios adds extra headers that conflict with S3 presigned URL signatures.
   Use XMLHttpRequest with explicit Content-Type: file.type || 'video/mp4'

4. CourseDetailPage.tsx — remove kbIngested check for AI Chat:
   Change: {canWatch && course.kbIngested && <AiChat courseId={course.id} />}
   To:     {canWatch && <AiChat courseId={course.id} />}

5. InstructorDashboardPage.tsx — add Delete button:
   Call courseService.remove(course.id) with confirmation dialog.
   Show confirmation: "Are you sure you want to delete {title}?"

6. VideoPlayer.tsx — smart progress intervals:
   const getInterval = (duration: number) => {
     if (duration <= 120) return 5000;   // <= 2 min: every 5s
     if (duration <= 600) return 10000;  // <= 10 min: every 10s
     return 15000;                        // > 10 min: every 15s
   }
   Also add seeked event and 25%/50%/75% milestone saves for short videos.

7. .env file required:
   VITE_API_BASE_URL=https://d1ka6o9mjvg4i9.cloudfront.net
```

---

## PROMPT 019 — Lambda Video Processor (NEW — not in original design)

**Files generated:**
- `lambda/olp-video-processor/lambda_function.py`

**Prompt:**
```
Generate an AWS Lambda function in Python 3.14 that:

1. Is triggered by S3 PUT events on bucket olp-videos-418272762620
2. Extracts the course ID from the S3 key path: videos/{courseId}/filename.mp4
3. Estimates video duration from file size (1MB ≈ 1 minute for 360p)
4. Calls PATCH /api/courses/{courseId}/upload-status?status=READY&durationSecs={n}
   on the ALB: http://olp-alb-1897172403.ap-south-1.elb.amazonaws.com
5. If the API call fails, marks the course as FAILED
6. Uses only standard library (urllib.request, urllib.parse) — no boto3 needed
7. Environment variable: ALB_URL (defaults to ALB endpoint)

S3 trigger configuration:
  Bucket: olp-videos-418272762620
  Event type: All object create events
  Prefix: videos/
  Suffix: .mp4

IMPORTANT: Use top-level imports only — do NOT import urllib inside the function.
Correct: import urllib.parse, import urllib.request at TOP of file.
```

---

## PROMPT 020 — SQS Progress Consumer (NEW — not in original design)

**Files generated:**
- `progress-service/src/aws/java/com/olp/progress/service/SqsProgressConsumer.java`

**Prompt:**
```
Generate an SQS consumer for progress-service in the Online Learning Portal.
Java 17, Spring Boot 3.2, AWS SDK v2.

@Service @Profile("!local") @RequiredArgsConstructor @Slf4j
class SqsProgressConsumer

Uses @Scheduled(fixedDelay = 5000) to poll every 5 seconds.
Requires @EnableScheduling on ProgressServiceApplication.

Injects:
  SqsClient sqsClient
  ObjectMapper objectMapper
  ProgressService progressService
  @Value aws.sqs.progress-queue-url String queueUrl

pollQueue() method:
  - ReceiveMessageRequest: maxNumberOfMessages=10, waitTimeSeconds=2, visibilityTimeout=30
  - For each message: deserialize ProgressEvent, call persistProgressEvent(), delete message
  - Log how many messages processed
  - Catch exceptions per-message (don't stop processing other messages)
  - Outer try-catch for SQS connection errors

This consumer processes the 42 messages that accumulate in the queue
and persists them to RDS PostgreSQL, enabling My Learning page to show
correct progress percentages.
```

---

## PROMPT 021 — buildspec.yml (CodeBuild)

**Files generated:**
- `olp_code/buildspec.yml`

**Prompt:**
```
Generate AWS CodeBuild buildspec.yml for the Online Learning Portal
multi-module Maven project.

Requirements:
- Java runtime: corretto17 (NOT openjdk — Corretto is AWS native)
- Pre-build: login to ECR using aws ecr get-login-password
- Build: mvn clean package -Paws -DskipTests for all 5 services
  Build each service separately with -pl common,{service}
- Docker build for each service using its Dockerfile
- Post-build: push all images to ECR, force-redeploy all ECS services
- ECR repo base: 418272762620.dkr.ecr.ap-south-1.amazonaws.com

IMPORTANT: Docker base images must use ECR Public mirror (NOT Docker Hub):
  Build stage:   public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-17
  Runtime stage: public.ecr.aws/docker/library/amazoncorretto:17-al2023
  
Reason: Docker Hub rate limits (429 Too Many Requests) in CodeBuild.
ECR Public has no rate limits.

ALSO: Remove USER/addgroup/adduser commands from Dockerfiles.
amazoncorretto:17-al2023 does not have addgroup/useradd commands.
```

---

## PROMPT 022 — Flyway V6 Migration (upload_status enum fix)

**Files generated:**
- `course-service/src/main/resources/db/migration/V6__fix_upload_status_constraint.sql`

**Prompt:**
```
Generate a Flyway V6 SQL migration for PostgreSQL 15 to fix the upload_status
column constraint in the courses table.

Problem: Java UploadStatus enum values are uppercase (READY, PENDING, etc.)
but the original V4 migration only allowed lowercase values.

Solution: Drop the existing check constraint and recreate it to accept both
uppercase and lowercase values for backward compatibility.

SQL:
ALTER TABLE courses DROP CONSTRAINT IF EXISTS courses_upload_status_check;
ALTER TABLE courses ADD CONSTRAINT courses_upload_status_check
  CHECK (upload_status IN (
    'NONE','PENDING','PROCESSING','READY','FAILED',
    'none','pending','processing','ready','failed'
  ));
```

---

## Architecture Decisions Made During Implementation

### 1. API Gateway → ALB (Simplified)
**Reason:** ALB handles path-based routing to 5 target groups. Injects x-user-id
and x-user-role headers from Cognito JWT. API Gateway would add cost and
complexity without significant benefit for internal microservices.

### 2. Bedrock KB (OpenSearch Serverless) → Direct Claude
**Reason:** OpenSearch Serverless minimum cost = 4 OCUs × $0.24/hr = $0.96/hr
($700/month). For demo environment, Claude answers directly as an AWS expert.
Production plan: Transcribe → S3 → KB ingestion Lambda → Bedrock KB → RAG.

### 3. SQS Consumer Added
**Reason:** Not in original design but required for My Learning progress to work.
Progress-service published to SQS but nothing consumed it. Added
SqsProgressConsumer with @Scheduled polling every 5 seconds.

### 4. Lambda Video Processor
**Reason:** Original design had this but implementation details were missing.
Implemented as Python 3.14 Lambda with S3 trigger. Marks course READY
automatically after video upload — no manual intervention needed.

### 5. ECR Public Mirror for Docker
**Reason:** Docker Hub rate limits (100 pulls/6h unauthenticated) caused 429
errors in CodeBuild. Switched to public.ecr.aws/docker/library/ which has
no rate limits for CodeBuild running in AWS.

### 6. Health Check Grace Period 300s
**Reason:** Spring Boot takes 90-130 seconds to start (loading JPA, Flyway,
Redis connections). Default grace period caused ECS to kill tasks before they
were ready. 300 seconds gives sufficient startup time.

### 7. Token Expiry Extended to 24h
**Reason:** Cognito default is 1 hour. During demo this caused frequent logouts.
Extended via update-user-pool-client with 24h access/id token validity.
IMPORTANT: Must also set explicit-auth-flows or the auth flow breaks.

---

## Dependency Versions Reference (FINAL)

```
Spring Boot:          3.2.0
Java:                 17 (Amazon Corretto)
PostgreSQL driver:    42.7.1
Flyway:               10.10.0 (NOT 9.x — Spring Boot 3.2 requires 10.x)
Lombok:               1.18.30
JJWT:                 0.12.3
AWS SDK v2 BOM:       2.21.0
Testcontainers:       1.19.3
MapStruct:            1.5.5.Final
Video.js:             8.10.0
React:                18.x
Vite:                 5.x
```

---

## AWS Resource IDs (for reference)

```
Account ID:           418272762620
Region:               ap-south-1
Cognito User Pool:    ap-south-1_oYypxwWMn
Cognito Client ID:    4uigtgilqddo9hh5boturapojg (no secret)
ALB DNS:              olp-alb-1897172403.ap-south-1.elb.amazonaws.com
CloudFront:           d1ka6o9mjvg4i9.cloudfront.net (E1ZZJFURH0TXEK)
ECR Base:             418272762620.dkr.ecr.ap-south-1.amazonaws.com
S3 Videos:            olp-videos-418272762620
S3 Assets:            olp-assets-418272762620
RDS Endpoint:         olp-postgres.czageo4kkd1g.ap-south-1.rds.amazonaws.com
Redis Endpoint:       master.olp-redis.0shmv9.aps1.cache.amazonaws.com:6379
SQS Progress Queue:   https://sqs.ap-south-1.amazonaws.com/418272762620/olp-progress-writes-queue
DynamoDB Table:       olp-ai-sessions
Lambda:               olp-video-processor
WAF ACL:              CreatedByCloudFront-34045166
GitHub:               https://github.com/reshmatiwari23/OnlineLearningPortal
```

---

## Demo Credentials

```
Instructor: reshma.tiwari9@gmail.com / Demo1234!
Learner:    reshma.tiwari@cognizant.com / Demo1234!
```

---

## Key Commands

```bash
# Scale down (save cost)
for svc in auth-service course-service enrollment-service progress-service ai-service; do
  aws ecs update-service --cluster olp-cluster --service $svc --desired-count 0 --region ap-south-1
done

# Scale up (before demo)
for svc in auth-service course-service enrollment-service progress-service ai-service; do
  aws ecs update-service --cluster olp-cluster --service $svc --desired-count 1 --region ap-south-1
done

# Delete NAT Gateway after presentation ($32/month saving)
aws ec2 delete-nat-gateway --nat-gateway-id nat-095c89d1c79890688 --region ap-south-1

# Frontend deploy
cd olp_frontend
echo "VITE_API_BASE_URL=https://d1ka6o9mjvg4i9.cloudfront.net" > .env
npm run build
aws s3 sync dist/ s3://olp-assets-418272762620/ --delete --exclude "index.html"
aws s3 cp dist/index.html s3://olp-assets-418272762620/index.html --cache-control "no-cache, no-store, must-revalidate"
aws cloudfront create-invalidation --distribution-id E1ZZJFURH0TXEK --paths "/*"
```
