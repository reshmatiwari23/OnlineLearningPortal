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
Cache:      ElastiCache Redis 7.x
Auth:       Amazon Cognito User Pool + JWT RS256
Storage:    Amazon S3
AI:         AWS Bedrock (Claude Sonnet 4.6, Titan Embeddings v2)
Container:  ECS Fargate (deployed via Docker)
Region:     ap-south-1 (Mumbai)
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

Plugin versions:
- spring-boot-maven-plugin: 3.2.0
- maven-compiler-plugin: 3.11.0

Java version: 17
Packaging: pom (parent)
GroupId: com.olp
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

Security:
  bcrypt-strength: 12

Logging:
  level root: INFO
  level com.olp: DEBUG
  pattern: structured JSON (include timestamp, level, logger, message, traceId)

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

Add a comment on the table explaining its purpose.
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
   - expiresIn = 86400 (24 hours in seconds)
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
     → sets custom:role attribute
     → returns the Cognito username (sub)
   - Method: authenticateUser(String email, String password)
     → calls initiateAuth with USER_PASSWORD_AUTH flow
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
   - Inject AuthService
   - POST /signup — accepts @Valid SignupRequest, returns ApiResponse<AuthResponse>
     HTTP 201 on success
   - POST /login  — accepts @Valid LoginRequest,  returns ApiResponse<AuthResponse>
     HTTP 200 on success
   - Both endpoints are PUBLIC (no JWT required)

2. SecurityConfig.java
   - @Configuration @EnableWebSecurity
   - SecurityFilterChain bean:
     - disable CSRF (REST API, stateless)
     - sessionManagement: STATELESS
     - permit: /api/auth/**, /actuator/health, /actuator/info
     - all other requests: authenticated
   - PasswordEncoder bean: BCryptPasswordEncoder(12)
   - Do NOT configure JWT filter here — JWT validation is done
     at API Gateway (Lambda Authoriser), not inside the service

3. AwsConfig.java
   - @Configuration
   - CognitoIdentityProviderClient bean
     - region from @Value cognito.region
     - uses DefaultCredentialsProvider (reads from
       ~/.aws/credentials locally, IAM role on ECS)

4. GlobalExceptionHandler.java
   - @RestControllerAdvice
   - Handle ResourceNotFoundException     → 404 with ApiResponse.error()
   - Handle DuplicateResourceException    → 409 with ApiResponse.error()
   - Handle UnauthorisedException         → 401 with ApiResponse.error()
   - Handle MethodArgumentNotValidException → 400 with field errors list
   - Handle generic Exception             → 500 with ApiResponse.error()

5. AuthServiceApplication.java
   - @SpringBootApplication
   - main method
```

---

## PROMPT 010 — auth-service Tests

**Files generated:**
- `auth-service/src/test/java/com/olp/auth/AuthControllerTest.java`

**Prompt:**
```
Generate JUnit 5 integration tests for auth-service AuthController.
Use MockMvc, Mockito, Spring Boot Test.
Do NOT use Testcontainers yet — mock the service layer.

Test cases:
1. signup_success
   POST /api/auth/signup with valid body
   Mock AuthService.signup() to return a valid AuthResponse
   Assert: HTTP 201, response body has token field, success = true

2. signup_duplicate_email
   POST /api/auth/signup with existing email
   Mock AuthService.signup() to throw DuplicateResourceException
   Assert: HTTP 409, success = false, message contains "already exists"

3. signup_invalid_email
   POST /api/auth/signup with malformed email
   Assert: HTTP 400, success = false (validation fails before service is called)

4. signup_short_password
   POST /api/auth/signup with 5-char password
   Assert: HTTP 400

5. login_success
   POST /api/auth/login with valid credentials
   Mock AuthService.login() to return a valid AuthResponse
   Assert: HTTP 200, token present

6. login_wrong_password
   POST /api/auth/login
   Mock AuthService.login() to throw UnauthorisedException
   Assert: HTTP 401

Use @WebMvcTest(AuthController.class) to load only the web layer.
Use @MockBean for AuthService.
```

---

## How to add new prompts

When you generate a new service, add a new section:

## PROMPT 0XX — [service-name] [component]

**Files generated:** list the files

**Prompt:**
\```
paste the exact prompt used
\```

---

## Dependency versions reference

```
Spring Boot:          3.2.0
Java:                 17
PostgreSQL driver:    42.7.1
Flyway:               9.22.3
Lombok:               1.18.30
JJWT:                 0.12.3
AWS SDK v2 BOM:       2.21.0
Testcontainers:       1.19.3
MapStruct:            1.5.5.Final
```

---

## PROMPT 011 — course-service POM

**Files generated:** `course-service/pom.xml`

**Prompt:**
```
Generate a Maven POM for the 'course-service' module of the online-learning-portal
multi-module project.

Parent: com.olp:online-learning-portal:1.0.0
ArtifactId: course-service  Port: 8082

Dependencies: common, spring-boot-starter-web, spring-boot-starter-data-jpa,
spring-boot-starter-security, spring-boot-starter-validation,
spring-boot-starter-actuator, postgresql, h2 (runtime), flyway-core,
flyway-database-postgresql, lombok, software.amazon.awssdk:s3,
software.amazon.awssdk:secretsmanager, jackson-databind,
spring-boot-starter-test, spring-security-test, testcontainers:postgresql

Main class: com.olp.course.CourseServiceApplication
```

---

## PROMPT 012 — course-service Entity, DTOs, Repository

**Files generated:**
- `course-service/src/main/java/com/olp/course/entity/Course.java`
- `course-service/src/main/java/com/olp/course/dto/CreateCourseRequest.java`
- `course-service/src/main/java/com/olp/course/dto/UpdateCourseRequest.java`
- `course-service/src/main/java/com/olp/course/dto/CourseResponse.java`
- `course-service/src/main/java/com/olp/course/dto/UploadUrlResponse.java`
- `course-service/src/main/java/com/olp/course/repository/CourseRepository.java`

**Prompt:**
```
Generate JPA entity and DTOs for course-service.
Table: courses with fields id(UUID), title, description, instructor_id(UUID),
instructor_name, video_url, video_duration(int seconds), thumbnail_url,
ai_summary(JSONB/JsonNode), kb_ingested(boolean), is_published(boolean),
created_at, updated_at.

UNIQUE constraint on (title, instructor_id).
Repository needs: findAllByIsPublishedTrue(Pageable),
findAllByInstructorId(UUID, Pageable), findByIdAndInstructorId(UUID, UUID),
existsByTitleAndInstructorId(String, UUID),
searchByTitle(@Query LIKE, Pageable).
```

---

## PROMPT 013 — course-service Service and Controller

**Files generated:**
- `course-service/src/main/java/com/olp/course/service/S3Service.java`
- `course-service/src/main/java/com/olp/course/service/CourseService.java`
- `course-service/src/main/java/com/olp/course/controller/CourseController.java`

**Prompt:**
```
Generate service and controller for course-service.
Endpoints: GET /api/courses (paginated), GET /api/courses/my (instructor),
GET /api/courses/search?keyword, GET /api/courses/{id},
POST /api/courses (instructor only), PUT /api/courses/{id} (owner only),
DELETE /api/courses/{id} (owner only),
POST /api/courses/{id}/upload-url (owner only, returns S3 presigned PUT URL).

Security: reads x-user-id and x-user-role headers from API Gateway.
Never validates JWT directly.
S3Service generates presigned PUT URL using AWS SDK v2 S3Presigner.
```

---

## PROMPT 014 — enrollment-service (complete)

**Files generated:**
- `enrollment-service/pom.xml`
- `enrollment-service/src/main/resources/application.yml`
- `enrollment-service/src/main/resources/application-local.yml`
- `enrollment-service/src/main/resources/db/migration/V3__create_enrollments.sql`
- `enrollment-service/src/main/resources/db/migration-h2/V3__create_enrollments.sql`
- `enrollment-service/src/main/java/com/olp/enrollment/entity/Enrollment.java`
- `enrollment-service/src/main/java/com/olp/enrollment/dto/EnrollmentResponse.java`
- `enrollment-service/src/main/java/com/olp/enrollment/repository/EnrollmentRepository.java`
- `enrollment-service/src/main/java/com/olp/enrollment/service/EnrollmentService.java`
- `enrollment-service/src/main/java/com/olp/enrollment/controller/EnrollmentController.java`
- `enrollment-service/src/main/java/com/olp/enrollment/config/SecurityConfig.java`
- `enrollment-service/src/main/java/com/olp/enrollment/exception/GlobalExceptionHandler.java`
- `enrollment-service/src/main/java/com/olp/enrollment/EnrollmentServiceApplication.java`
- `enrollment-service/src/test/java/com/olp/enrollment/EnrollmentControllerTest.java`

**Prompt:**
```
Generate complete enrollment-service for Online Learning Portal.
Port: 8083. Java 17, Spring Boot 3.2.

Table: enrollments (id UUID, course_id UUID, user_id UUID,
enrolled_at TIMESTAMPTZ, completed_at TIMESTAMPTZ)
UNIQUE constraint: (course_id, user_id)

Endpoints:
  POST   /api/enrollment/{courseId}         — enrol (learner only, 409 on duplicate)
  DELETE /api/enrollment/{courseId}         — unenrol (404 if not enrolled)
  GET    /api/enrollment/my                 — list all enrollments for user
  GET    /api/enrollment/{courseId}/status  — boolean: is user enrolled?
  GET    /api/enrollment/{courseId}/count   — learner count for a course

Double-check pattern: app-level existsByCourseIdAndUserId check for friendly 409,
plus catch DataIntegrityViolationException for race condition safety.
Reads x-user-id and x-user-role headers from API Gateway.
Only learners (role=user) can enrol and unenrol.
H2 local profile, MockS3 not needed (no S3 calls in this service).
```

---

## PROMPT 015 — upload_status column + UploadStatus enum

**Files generated/updated:**
- `course-service/src/main/resources/db/migration/V4__add_upload_status.sql`
- `course-service/src/main/resources/db/migration-h2/V4__add_upload_status.sql`
- `course-service/src/main/java/com/olp/course/entity/UploadStatus.java`  (new)
- `course-service/src/main/java/com/olp/course/entity/Course.java`  (added uploadStatus field)
- `course-service/src/main/java/com/olp/course/dto/CourseResponse.java`  (added uploadStatus)
- `course-service/src/main/java/com/olp/course/service/CourseService.java`  (set PENDING on URL issue, added updateUploadStatus)
- `course-service/src/main/java/com/olp/course/controller/CourseController.java`  (added PATCH /{id}/upload-status)

**Prompt:**
```
Add upload_status lifecycle tracking to course-service.

Add V4 Flyway migration: ALTER TABLE courses ADD COLUMN upload_status VARCHAR(50)
DEFAULT 'none' with CHECK constraint (none/pending/processing/ready/failed).

Add UploadStatus Java enum with values NONE, PENDING, PROCESSING, READY, FAILED.

Add uploadStatus field to Course entity (@Enumerated(STRING)).
Add uploadStatus to CourseResponse DTO.

Update CourseService.generateUploadUrl() to set status = PENDING when URL is issued.
Add CourseService.updateUploadStatus(courseId, status, durationSecs):
  - Sets the new status
  - Sets videoDuration when status = READY
  - Clears videoUrl when status = FAILED (so instructor can re-upload)

Add PATCH /api/courses/{id}/upload-status endpoint to CourseController.
This is an internal endpoint called by the video-processor Lambda.
```

---

## PROMPT 016 — progress-service (complete)

**Files generated:**
- All files under `progress-service/`

**Prompt:**
```
Generate complete progress-service for Online Learning Portal.
Port: 8084. Java 17, Spring Boot 3.2.

Write strategy:
  HOT PATH  → Redis (synchronous, StringRedisTemplate, TTL 60s)
  COLD PATH → RDS via SQS (async, fire-and-forget after Redis write)

Table: video_progress (id UUID, user_id UUID, course_id UUID,
current_time_secs INT, duration_secs INT, percent_complete INT 0-100,
last_updated_at TIMESTAMPTZ, completed_at TIMESTAMPTZ)
UNIQUE(user_id, course_id)

Redis key: progress:{userId}:{courseId} → percentComplete string

Services:
  RedisProgressService — writeProgress, readProgress, deleteProgress
  SqsProgressPublisher — publish(ProgressEvent), fire-and-forget, swallows errors
  ProgressService      — updateProgress (Redis + SQS), getProgress (Redis → RDS),
                         getAllProgressForUser, getAllProgressForCourse,
                         persistProgressEvent (SQS consumer UPSERT to RDS)

Endpoints:
  POST /api/progress/{courseId}         — update (Video.js every 5s)
  GET  /api/progress/{courseId}         — read (Redis → RDS fallback)
  GET  /api/progress/my                 — all for user
  GET  /api/progress/{courseId}/all     — all learners for course (instructor only)

Local profile: mock SqsClient logs + immediately calls persistProgressEvent
to simulate the consumer. No real Redis needed — use embedded map.
H2 in-memory database.
```

---

## PROMPT 017 — ai-service (complete)

**Files generated:**
- All files under `ai-service/`

**Prompt:**
```
Generate complete ai-service for Online Learning Portal.
Port: 8085. Java 17, Spring Boot 3.2.

This is the ONLY service with AWS Bedrock IAM permissions.

AWS SDK dependencies:
  - bedrockruntime (Claude + Titan direct API calls)
  - bedrockagentruntime (Knowledge Base RAG retrieval)
  - dynamodb (chat session history, 24h TTL)
  - ses (nudge emails)

Models:
  - Claude Sonnet 4.6: RAG chat, recommendations, auto summary
  - Claude Haiku 4.5: progress nudges (20x cheaper)
  - Titan Embeddings v2: semantic search + recommendations vectors

Features:
  1. RAG Chat (SSE streaming): POST /ai/chat
     - BedrockAgentRuntime KB retrieve (cosine ≥ 0.75, top-5 chunks)
     - Augmented prompt → Claude Sonnet stream via SSE SseEmitter
     - Citations [{chunkId, timestampSeconds, score, excerpt}]

  2. Recommendations: GET /ai/recommend
     - Build user profile text from enrolled topics
     - Titan embed → kNN → Claude Sonnet ranks top-3 + reasons
     - Redis cache rec:{userId} TTL 1h, invalidated on enrolment

  3. Semantic Search: GET /ai/search
     - Titan embed query → return 1536-dim vector (caller does kNN)

  4. Auto Summary: POST /ai/summary (internal Lambda endpoint)
     - Claude Sonnet JSON mode → {title, objectives[], summary, difficulty, keyTakeaway}

  5. Progress Nudge: POST /ai/nudge (internal scheduler endpoint)
     - Claude Haiku generates 2-sentence message
     - SES sends HTML email

Local profile: MockBedrockRuntimeClient (fake 1536-dim embedding),
MockBedrockAgentRuntimeClient (returns 2 fake chunks),
MockSesClient (logs instead of sending), MockDynamoDbClient.
```

---

## PROMPT 018 — React 18 Frontend (complete)

**Location:** `olp-frontend/` (separate from the Spring Boot project)

**Files generated:**
- `package.json`, `vite.config.ts`, `tsconfig.json`, `index.html`
- `src/types/index.ts` — all TypeScript interfaces
- `src/services/` — api.ts, authService, courseService, enrollmentService, aiService
- `src/context/AuthContext.tsx` — global auth state + JWT storage
- `src/styles/global.css` — CSS variables + resets
- `src/components/layout/Navbar.tsx + .module.css`
- `src/components/common/Button.tsx, Input.tsx + .module.css`
- `src/components/course/CourseCard.tsx, VideoPlayer.tsx + .module.css`
- `src/components/ai/AiChat.tsx + .module.css` — SSE streaming chat drawer
- `src/pages/auth/LoginPage.tsx, SignupPage.tsx`
- `src/pages/learner/CourseCataloguePage.tsx, CourseDetailPage.tsx, MyLearningPage.tsx`
- `src/pages/instructor/InstructorDashboardPage.tsx`
- `src/App.tsx` — router with protected routes
- `src/main.tsx` — React entry point

**Prompt:**
```
Generate a complete React 18 + TypeScript + Vite frontend for an Online Learning Portal.
Use plain CSS modules (no Tailwind). Use react-router-dom v6, axios, video.js.

Pages:
  /login        — LoginPage (email + password → POST /api/auth/login)
  /signup       — SignupPage (name, email, password, role toggle learner/instructor)
  /courses      — CourseCataloguePage (paginated grid, keyword search)
  /courses/:id  — CourseDetailPage (VideoPlayer, enrol, progress bar, AI chat drawer)
  /my-learning  — MyLearningPage (enrolled courses with progress)
  /instructor   — InstructorDashboardPage (create course modal, upload video to S3)

Key features:
  - AuthContext with JWT stored in localStorage
  - Protected routes redirect to /login if not authenticated
  - VideoPlayer.tsx: Video.js player that reports progress every 5 seconds
    via POST /api/progress/{courseId}
  - AiChat.tsx: SSE streaming drawer that opens from a floating button,
    shows tokens one by one as Claude generates them, shows citations at end
  - Video upload: get S3 presigned URL → PUT directly to S3 with progress %
  - Role-based navigation: instructors see My Courses, learners see My Learning
  - Vite proxy: /api/* → localhost:8081-8084, /ai → localhost:8085
```
