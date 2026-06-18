# Online Learning Portal (OLP)

A cloud-native Learning Management System built on Java 17 + Spring Boot 3.2
microservices with a React 18 frontend and AWS Bedrock Gen AI.

---

## Table of Contents

1. [Project Structure](#project-structure)
2. [Technology Stack](#technology-stack)
3. [Running Locally — Backend](#running-locally--backend)
4. [Running Locally — Frontend](#running-locally--frontend)
5. [AWS Infrastructure Values](#aws-infrastructure-values)
6. [AWS Deployment — Step by Step](#aws-deployment--step-by-step)
7. [Environment Variables Reference](#environment-variables-reference)
8. [Service Reference](#service-reference)
9. [Database Migrations](#database-migrations)
10. [Common Errors and Fixes](#common-errors-and-fixes)

---

## Project Structure

```
online-learning-portal/
│
├── olp_code/                          ← Spring Boot backend (Maven multi-module)
│   ├── pom.xml                        ← parent POM — all dependency versions
│   ├── README.md                      ← this file
│   ├── PROMPTS.md                     ← AI generation prompts log
│   ├── deploy.sh                      ← one-command ECR push + ECS deploy
│   ├── docker-compose.yml             ← run all services locally with Docker
│   │
│   ├── common/                        ← shared library (DTOs, exceptions)
│   │
│   ├── auth-service/       port 8081  ← signup, login, JWT via Cognito
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/com/olp/auth/
│   │       │   ├── controller/AuthController.java
│   │       │   ├── service/AuthService.java
│   │       │   ├── service/CognitoService.java
│   │       │   ├── config/SecurityConfig.java
│   │       │   ├── config/local/MockCognitoService.java  ← active on local profile
│   │       │   ├── entity/User.java
│   │       │   ├── dto/SignupRequest.java
│   │       │   ├── dto/LoginRequest.java
│   │       │   └── dto/AuthResponse.java
│   │       ├── aws/java/com/olp/auth/config/
│   │       │   └── AwsConfig.java     ← real Cognito client (aws profile only)
│   │       └── resources/
│   │           ├── application.yml
│   │           ├── application-local.yml
│   │           ├── db/migration/V1__init.sql         ← PostgreSQL
│   │           └── db/migration-h2/V1__init.sql      ← H2 (local)
│   │
│   ├── course-service/     port 8082  ← CRUD, S3 upload, upload status
│   │   ├── Dockerfile
│   │   └── src/main/java/com/olp/course/
│   │       ├── entity/Course.java
│   │       ├── entity/UploadStatus.java   ← NONE/PENDING/PROCESSING/READY/FAILED
│   │       ├── service/CourseService.java
│   │       ├── service/S3Service.java     ← generates S3 presigned PUT URLs
│   │       └── config/local/MockS3Config.java
│   │
│   ├── enrollment-service/ port 8083  ← enrol/unenrol, duplicate prevention
│   │   ├── Dockerfile
│   │   └── src/main/java/com/olp/enrollment/
│   │       └── service/EnrollmentService.java  ← double-check UNIQUE constraint
│   │
│   ├── progress-service/   port 8084  ← Redis-first writes, 2000/sec capacity
│   │   ├── Dockerfile
│   │   └── src/main/java/com/olp/progress/
│   │       ├── service/ProgressService.java       ← orchestrates Redis+SQS+RDS
│   │       ├── service/RedisProgressService.java  ← hot path write
│   │       └── service/SqsProgressPublisher.java  ← async RDS flush
│   │
│   └── ai-service/         port 8085  ← ONLY service with Bedrock permissions
│       ├── Dockerfile
│       └── src/main/java/com/olp/ai/
│           ├── service/BedrockService.java         ← all Claude + Titan calls
│           ├── service/RecommendationService.java
│           └── service/NudgeService.java
│
└── olp_frontend/                      ← React 18 SPA
    ├── package.json
    ├── vite.config.ts                 ← dev proxy: /api/* → :8081-8085
    ├── tsconfig.json
    ├── index.html
    └── src/
        ├── types/index.ts             ← all TypeScript interfaces
        ├── context/AuthContext.tsx    ← JWT stored in localStorage
        ├── services/
        │   ├── api.ts                 ← Axios instance + JWT interceptor
        │   ├── authService.ts
        │   ├── courseService.ts       ← includes S3 direct upload
        │   ├── enrollmentService.ts
        │   └── aiService.ts           ← SSE streaming via fetch ReadableStream
        ├── components/
        │   ├── layout/Navbar.tsx
        │   ├── common/Button.tsx, Input.tsx
        │   ├── course/CourseCard.tsx
        │   ├── course/VideoPlayer.tsx  ← Video.js + 5s progress reporting
        │   └── ai/AiChat.tsx           ← floating button + SSE drawer + citations
        └── pages/
            ├── auth/LoginPage.tsx
            ├── auth/SignupPage.tsx
            ├── learner/CourseCataloguePage.tsx
            ├── learner/CourseDetailPage.tsx
            ├── learner/MyLearningPage.tsx
            └── instructor/InstructorDashboardPage.tsx
```

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend language | Java 17 |
| Backend framework | Spring Boot 3.2.0 |
| Build tool | Maven multi-module |
| Database | PostgreSQL 15 (AWS RDS) / H2 in-memory (local) |
| Cache | Redis 7 (AWS ElastiCache) |
| Message queue | Amazon SQS |
| Auth | Amazon Cognito + JWT RS256 |
| Object storage | Amazon S3 + CloudFront |
| AI — LLM | Claude Sonnet 4.6, Claude Haiku 4.5 (AWS Bedrock, us-east-1) |
| AI — Embeddings | Amazon Titan Embeddings v2 |
| AI — Vector store | OpenSearch Serverless (Bedrock Knowledge Base) |
| Email | Amazon SES |
| Frontend | React 18, TypeScript, Vite 5 |
| Frontend routing | React Router v6 |
| Video player | Video.js 8 |
| HTTP client | Axios |
| CSS | Plain CSS modules |
| Container | Docker, ECS Fargate |
| Primary region | ap-south-1 (Mumbai) |
| DR region | ap-southeast-1 (Singapore) |

---

## Running Locally — Backend

All services use **H2 in-memory database** and **mock AWS clients** on the
local profile. No AWS credentials or internet access needed.

### Prerequisites

```bash
java -version    # must show 17.x.x
mvn -version     # must show 3.9.x
```

### Step 1 — Build (works offline on corporate network)

```bash
cd olp_code
set SPRING_PROFILES_ACTIVE=local
mvn clean install -DskipTests
```

This downloads only Spring Boot jars — no AWS SDK. Works on corporate network.

### Step 2 — Run each service (separate terminal per service)

```bash
# Terminal 1
set SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run -pl auth-service

# Terminal 2
set SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run -pl course-service

# Terminal 3
set SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run -pl enrollment-service

# Terminal 4
set SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run -pl progress-service

# Terminal 5
set SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run -pl ai-service
```

### Step 3 — Verify all services are running

Open each URL in your browser — all should return {"status":"UP"}:

| Service | Port | Health URL |
|---|---|---|
| auth-service | 8081 | http://localhost:8081/actuator/health |
| course-service | 8082 | http://localhost:8082/actuator/health |
| enrollment-service | 8083 | http://localhost:8083/actuator/health |
| progress-service | 8084 | http://localhost:8084/actuator/health |
| ai-service | 8085 | http://localhost:8085/actuator/health |

### Step 4 — View H2 database in browser (optional)

| Service | H2 Console URL | JDBC URL | Username | Password |
|---|---|---|---|---|
| auth-service | http://localhost:8081/h2-console | jdbc:h2:mem:olp_db | sa | (blank) |
| course-service | http://localhost:8082/h2-console | jdbc:h2:mem:olp_db | sa | (blank) |
| enrollment-service | http://localhost:8083/h2-console | jdbc:h2:mem:olp_db | sa | (blank) |
| progress-service | http://localhost:8084/h2-console | jdbc:h2:mem:olp_db | sa | (blank) |

### Maven Profiles

| Profile | Activate with | What is included | Use when |
|---|---|---|---|
| `local` (default) | `set SPRING_PROFILES_ACTIVE=local` | Spring Boot + H2 + mocks. No AWS SDK. | Local development. Works offline. |
| `aws` | `mvn -Paws` | Full AWS SDK added. | Building Docker images for deployment. Needs internet. |

---

## Running Locally — Frontend

### Prerequisites

```bash
node -v    # must show v18.x.x or v20.x.x
```

### Step 1 — Install dependencies

```bash
cd olp_frontend
npm install
```

### Step 2 — Configure API base URL

For local development no configuration is needed — Vite's dev proxy
automatically forwards API calls to the running Spring Boot services.

The proxy is configured in `vite.config.ts`:

```
/api/auth/*       → http://localhost:8081
/api/courses/*    → http://localhost:8082
/api/enrollment/* → http://localhost:8083
/api/progress/*   → http://localhost:8084
/ai/*             → http://localhost:8085
```

### Step 3 — Start the dev server

```bash
npm run dev
```

Opens at: **http://localhost:5173**

### Step 4 — Create a test account

1. Go to http://localhost:5173
2. Click **Create one** → fill in name, email, password
3. Select **Learner** or **Instructor**
4. Click **Create account**

On local profile the MockCognitoService returns a fake JWT — no real AWS needed.

### Frontend Pages

| URL | Page | Who can access |
|---|---|---|
| /login | Login | Public |
| /signup | Signup | Public |
| /courses | Course catalogue (paginated, searchable) | Any logged-in user |
| /courses/:id | Course detail (video player, enrol, AI chat) | Any logged-in user |
| /my-learning | Enrolled courses with progress bars | Learner only |
| /instructor | Create course, upload video, view drafts | Instructor only |

---

## AWS Infrastructure Values

Fill in these values as you complete the AWS Console Setup Guide.
You will need them for environment variables and deployment.

### Account and Region

```
AWS Account ID:              ___________________________
Primary Region:              ap-south-1
Bedrock Region:              us-east-1  (model access managed here)
```

### VPC and Networking

```
VPC Name:                    olp-vpc
VPC ID:                      vpc-___________________________
VPC CIDR:                    10.10.0.0/16

Subnet — olp-public-1a:      subnet-___________________________ (ap-south-1a)
Subnet — olp-public-1b:      subnet-___________________________ (ap-south-1b)
Subnet — olp-private-1a:     subnet-___________________________ (ap-south-1a)
Subnet — olp-private-1b:     subnet-___________________________ (ap-south-1b)
```

### Security Groups

```
olp-alb-sg   (ALB):          sg-___________________________
  Inbound:   HTTPS 443 from 0.0.0.0/0
             HTTP  80  from 0.0.0.0/0

olp-ecs-sg   (ECS tasks):    sg-___________________________
  Inbound:   All TCP from olp-alb-sg

olp-rds-sg   (RDS):          sg-___________________________
  Inbound:   TCP 5432 from olp-ecs-sg only

olp-redis-sg (ElastiCache):  sg-___________________________
  Inbound:   TCP 6379 from olp-ecs-sg only
```

### IAM Roles

```
olp-ecs-execution-role       ← allows ECS to pull ECR images + write CloudWatch logs
                               Attach: AmazonECSTaskExecutionRolePolicy

olp-auth-task-role           ← used by auth-service container
                               Attach: AmazonRDSDataFullAccess, SecretsManagerReadWrite

olp-course-task-role         ← used by course-service container
                               Attach: AmazonRDSDataFullAccess, AmazonS3FullAccess,
                                       SecretsManagerReadWrite

olp-enroll-task-role         ← used by enrollment-service container
                               Attach: AmazonRDSDataFullAccess, SecretsManagerReadWrite

olp-progress-task-role       ← used by progress-service container
                               Attach: AmazonRDSDataFullAccess, AmazonElastiCacheFullAccess,
                                       AmazonSQSFullAccess, SecretsManagerReadWrite

olp-ai-task-role             ← used by ai-service container — ONLY role with Bedrock
                               Attach: AmazonBedrockFullAccess, AmazonDynamoDBFullAccess,
                                       AmazonOpenSearchServiceFullAccess, SecretsManagerReadWrite
```

### RDS PostgreSQL

```
DB Instance ID:              olp-postgres
Endpoint:                    _____________________________.ap-south-1.rds.amazonaws.com
Port:                        5432
Database name:               olp_db
Master username:             olp_admin
Master password:             (stored in Secrets Manager: olp/rds/credentials)
Instance class:              db.t3.medium (dev) / db.r6g.large (prod)
Multi-AZ:                    enabled
Subnets:                     olp-private-1a, olp-private-1b
Security group:              olp-rds-sg
```

### ElastiCache Redis

```
Cluster name:                olp-redis
Primary endpoint:            _____________________________.cache.amazonaws.com
Port:                        6379
Engine version:              Redis 7.x
Node type:                   cache.t3.micro (dev) / cache.r6g.large (prod)
TLS:                         enabled (in-transit encryption)
Subnets:                     olp-private-1a, olp-private-1b
Security group:              olp-redis-sg
```

### SQS Queues

```
Video processing queue:
  Name:    olp-video-processing-queue
  URL:     https://sqs.ap-south-1.amazonaws.com/[account-id]/olp-video-processing-queue
  DLQ:     olp-video-dlq

Progress writes queue:
  Name:    olp-progress-writes-queue
  URL:     https://sqs.ap-south-1.amazonaws.com/[account-id]/olp-progress-writes-queue
  DLQ:     olp-progress-dlq
```

### S3 Buckets

```
Videos bucket:               olp-videos-[account-id]
  CORS:                      configured for localhost:5173 and production domain
  Block public access:       ON

Transcripts bucket:          olp-transcripts-[account-id]
  Block public access:       ON

Assets bucket (frontend):    olp-assets-[account-id]
  Block public access:       ON (served via CloudFront only)
```

### ECR Repositories

```
Registry URL:                [account-id].dkr.ecr.ap-south-1.amazonaws.com

Repositories:
  olp/auth-service
  olp/course-service
  olp/enrollment-service
  olp/progress-service
  olp/ai-service
```

### ECS

```
Cluster name:                olp-cluster
Infrastructure:              AWS Fargate

Services (one per microservice):
  auth-service        task definition: olp-auth-service        port: 8081
  course-service      task definition: olp-course-service      port: 8082
  enrollment-service  task definition: olp-enrollment-service  port: 8083
  progress-service    task definition: olp-progress-service    port: 8084
  ai-service          task definition: olp-ai-service          port: 8085

Each task definition requires:
  executionRoleArn:   arn:aws:iam::[account-id]:role/olp-ecs-execution-role
  taskRoleArn:        arn:aws:iam::[account-id]:role/olp-[service]-task-role
```

### Application Load Balancer

```
ALB name:                    olp-alb
DNS name:                    olp-alb-xxxxxxx.ap-south-1.elb.amazonaws.com
Scheme:                      Internet-facing
Subnets:                     olp-public-1a, olp-public-1b
Security group:              olp-alb-sg
Listener:                    HTTP:80 → redirect to HTTPS:443
```

### Cognito

```
User Pool name:              olp-user-pool
User Pool ID:                ap-south-1_XXXXXXXXX
App Client name:             olp-app-client
App Client ID:               ___________________________
Issuer URL:                  https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_XXXXXXXXX
Custom attribute:            custom:role  (required — must be added to User Pool)
Auth flows:                  ALLOW_USER_PASSWORD_AUTH, ALLOW_REFRESH_TOKEN_AUTH
```

### API Gateway

```
API name:                    olp-api
Type:                        HTTP API
Stage:                       dev
API URL:                     https://[api-id].execute-api.ap-south-1.amazonaws.com/dev

JWT Authoriser:              olp-cognito-authoriser
  Issuer:                    https://cognito-idp.ap-south-1.amazonaws.com/[pool-id]
  Audience:                  [app-client-id]

Public routes (no auth):
  POST /api/auth/signup
  POST /api/auth/login

Protected routes (JWT required):
  ANY /api/courses/{proxy+}
  ANY /api/enrollment/{proxy+}
  ANY /api/progress/{proxy+}
  ANY /ai/{proxy+}
```

### Bedrock and AI

```
Bedrock region:              us-east-1
Models (submit usage form for each):
  Claude Sonnet 4.6:         claude-sonnet-4-6
  Claude Haiku 4.5:          claude-haiku-4-5-20251001
  Titan Embeddings v2:       amazon.titan-embed-text-v2:0  (no form needed)

Knowledge Base name:         olp-course-kb
Knowledge Base ID:           ___________________________
S3 source:                   s3://olp-transcripts-[account-id]/
Chunking:                    512 tokens, 12% overlap
Embedding dimensions:        1536

OpenSearch collection:       olp-course-vectors
OpenSearch endpoint:         https://[id].ap-south-1.aoss.amazonaws.com

DynamoDB table:              olp-ai-sessions  (chat session history, TTL 24h)
```

### Secrets Manager

```
olp/rds/credentials          RDS username + password
olp/redis/config             Redis host + port
olp/cognito/config           User Pool ID + Client ID + region
olp/s3/config                Bucket names
olp/bedrock/config           Knowledge Base ID + OpenSearch endpoint + region
```

### CloudWatch Log Groups

```
/olp/auth-service            Retention: 30 days
/olp/course-service          Retention: 30 days
/olp/enrollment-service      Retention: 30 days
/olp/progress-service        Retention: 30 days
/olp/ai-service              Retention: 30 days
```

---

## AWS Deployment — Step by Step

### Prerequisites

- AWS CLI configured (`aws configure`)
- Docker Desktop running
- Internet access (use mobile hotspot if corporate network blocks Maven Central)
- All AWS infrastructure created (see AWS_Console_Setup_Guide_OLP_v2.docx)

### Step 1 — Configure deploy.sh

Open `olp_code/deploy.sh` and replace the placeholder values:

```bash
ACCOUNT_ID="123456789012"      ← your real AWS account ID
REGION="ap-south-1"
```

### Step 2 — Build and deploy backend (all services)

```bash
cd olp_code
./deploy.sh
```

This single command:
1. Logs in to ECR
2. Builds Maven with `-Paws` (full AWS SDK)
3. Builds Docker image for each service (multi-stage build)
4. Pushes each image to ECR
5. Forces ECS to deploy the new image

Deploy a single service only:

```bash
./deploy.sh auth-service
```

### Step 3 — Set environment variables on ECS

For each service go to:
**ECS → olp-cluster → Task Definitions → [service] → Create new revision → Container → Environment variables**

Set the variables listed in the Environment Variables Reference section below.

### Step 4 — Start ECS services in correct order

Flyway runs DB migrations on startup. Start in this exact order:

```
1. auth-service        → V1 migration creates users table
2. course-service      → V2 + V4 migrations create courses table
3. enrollment-service  → V3 migration creates enrollments table
4. progress-service    → V5 migration creates video_progress table
5. ai-service          → no DB migrations, start after Redis is confirmed running
```

Wait for each service to show **RUNNING** and health check **HEALTHY**
before starting the next one.

Check logs if a service fails:
```
CloudWatch → Log groups → /olp/[service-name] → latest log stream
```

### Step 5 — Build and deploy frontend

```bash
cd olp_frontend

# Create .env file with your API Gateway URL
echo "VITE_API_BASE_URL=https://[api-id].execute-api.ap-south-1.amazonaws.com/dev" > .env

# Install dependencies
npm install

# Build production bundle
npm run build
# Output: olp_frontend/dist/ folder

# Upload to S3
aws s3 sync dist/ s3://olp-assets-[account-id]/ --delete

# Invalidate CloudFront cache so users see the new version
aws cloudfront create-invalidation \
  --distribution-id YOUR_CLOUDFRONT_DISTRIBUTION_ID \
  --paths "/*"
```

### Step 6 — Verify deployment

Test the full flow through API Gateway:

```bash
# Signup
curl -X POST https://[api-id].execute-api.ap-south-1.amazonaws.com/dev/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test1234!","name":"Test User","role":"user"}'

# Expected response
{"success":true,"data":{"token":"eyJ...","userId":"...","role":"user"},"timestamp":"..."}
```

Check all 5 services:

```bash
curl https://[api-id].execute-api.ap-south-1.amazonaws.com/dev/actuator/health
# Each service returns {"status":"UP"}
```

---

## Environment Variables Reference

Set these in each ECS Task Definition under Container → Environment variables.

### auth-service (port 8081)

| Variable | Value | Where to get it |
|---|---|---|
| DB_HOST | your-rds-endpoint.ap-south-1.rds.amazonaws.com | RDS console → olp-postgres → Endpoint |
| DB_USERNAME | olp_admin | Set during RDS creation |
| DB_PASSWORD | your-rds-password | Secrets Manager → olp/rds/credentials |
| DB_NAME | olp_db | Set during RDS creation |
| COGNITO_REGION | ap-south-1 | Fixed value |
| COGNITO_USER_POOL_ID | ap-south-1_XXXXXXXXX | Cognito → olp-user-pool → Pool ID |
| COGNITO_CLIENT_ID | 26-character string | Cognito → olp-user-pool → App clients → olp-app-client |

### course-service (port 8082)

All DB variables from auth-service, plus:

| Variable | Value | Where to get it |
|---|---|---|
| AWS_REGION | ap-south-1 | Fixed value |
| S3_VIDEOS_BUCKET | olp-videos-[account-id] | S3 console → bucket name |

### enrollment-service (port 8083)

All DB variables from auth-service only. No additional AWS services.

### progress-service (port 8084)

All DB variables from auth-service, plus:

| Variable | Value | Where to get it |
|---|---|---|
| AWS_REGION | ap-south-1 | Fixed value |
| REDIS_HOST | olp-redis.xxx.cache.amazonaws.com | ElastiCache → olp-redis → Primary endpoint (without port) |
| REDIS_PORT | 6379 | Fixed value |
| REDIS_SSL | true | Fixed value (ElastiCache requires TLS) |
| REDIS_PASSWORD | your-auth-token | ElastiCache auth token if enabled |
| SQS_PROGRESS_QUEUE_URL | https://sqs.ap-south-1.amazonaws.com/[account]/olp-progress-writes-queue | SQS console → olp-progress-writes-queue → URL |

### ai-service (port 8085)

| Variable | Value | Where to get it |
|---|---|---|
| AWS_REGION | ap-south-1 | Fixed value |
| REDIS_HOST | olp-redis.xxx.cache.amazonaws.com | ElastiCache primary endpoint (without port) |
| REDIS_PORT | 6379 | Fixed value |
| REDIS_SSL | true | Fixed value |
| REDIS_PASSWORD | your-auth-token | ElastiCache auth token if enabled |
| BEDROCK_REGION | us-east-1 | Fixed value (Bedrock model access is in us-east-1) |
| BEDROCK_KB_ID | 10-character string | Bedrock → Knowledge Bases → olp-course-kb → ID |
| DYNAMODB_REGION | ap-south-1 | Fixed value |
| DYNAMODB_SESSIONS_TABLE | olp-ai-sessions | Fixed value (create this table in DynamoDB) |

### Frontend (.env file for production build)

Create `olp_frontend/.env` before running `npm run build`:

```env
VITE_API_BASE_URL=https://[api-id].execute-api.ap-south-1.amazonaws.com/dev
```

Do NOT commit this file to Git. It is in `.gitignore` already.

---

## Service Reference

### auth-service — Port 8081

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | /api/auth/signup | None | Register. Body: {email, password, name, role} |
| POST | /api/auth/login | None | Login. Body: {email, password} |

Both endpoints return: `{success, data: {token, userId, email, name, role, expiresIn}}`

---

### course-service — Port 8082

All endpoints read `x-user-id` and `x-user-role` headers injected by API Gateway.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | /api/courses | Any | Paginated published courses. Params: page, size |
| GET | /api/courses/{id} | Any | Single course |
| GET | /api/courses/search?keyword= | Any | Search by title |
| GET | /api/courses/my | Instructor | Instructor's own courses |
| POST | /api/courses | Instructor | Create course. Body: {title, description} |
| PUT | /api/courses/{id} | Instructor | Update. Body: {title, description, isPublished} |
| DELETE | /api/courses/{id} | Instructor | Delete |
| POST | /api/courses/{id}/upload-url?fileName= | Instructor | S3 presigned PUT URL |
| PATCH | /api/courses/{id}/upload-status?status= | Internal | Lambda callback |

Upload status values: `none → pending → processing → ready or failed`

---

### enrollment-service — Port 8083

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | /api/enrollment/{courseId} | Learner | Enrol. Returns 409 if already enrolled. |
| DELETE | /api/enrollment/{courseId} | Learner | Unenrol. Returns 404 if not enrolled. |
| GET | /api/enrollment/my | Any | All enrollments for current user |
| GET | /api/enrollment/{courseId}/status | Any | Boolean: is user enrolled? |
| GET | /api/enrollment/{courseId}/count | Any | Learner count for a course |

---

### progress-service — Port 8084

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | /api/progress/{courseId} | Any | Update progress. Writes to Redis (sync) then SQS (async). |
| GET | /api/progress/{courseId} | Any | Get progress. Redis first, RDS fallback. |
| GET | /api/progress/my | Any | All progress records for current user |
| GET | /api/progress/{courseId}/all | Instructor | All learner progress for a course |

Request body for POST: `{currentTimeSecs, durationSecs}`

---

### ai-service — Port 8085

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | /ai/chat | Any | RAG chat. Returns SSE stream of tokens. |
| GET | /ai/recommend | Any | Top 3 recommendations (Redis-cached 1h) |
| GET | /ai/search?query= | Any | Returns 1536-dim embedding vector |
| POST | /ai/summary | Internal | Generate course summary (Lambda calls this) |
| POST | /ai/nudge | Internal | Send nudge email (scheduler calls this) |

Models: Claude Sonnet 4.6 (chat, summary, recommendations), Claude Haiku 4.5 (nudges), Titan Embeddings v2 (search, recommendations)

---

## Database Migrations

Flyway runs migrations automatically on each service startup.
Start services in order 1→5 for first deployment.

| File | Service | What it creates | Run by |
|---|---|---|---|
| V1__init.sql | auth-service | users table | auth-service on startup |
| V2__create_courses.sql | course-service | courses table with JSONB ai_summary | course-service on startup |
| V3__create_enrollments.sql | enrollment-service | enrollments table | enrollment-service on startup |
| V4__add_upload_status.sql | course-service | upload_status column | course-service on startup |
| V5__create_progress.sql | progress-service | video_progress table | progress-service on startup |

Two versions per migration:
- `db/migration/` — PostgreSQL syntax (AWS RDS)
- `db/migration-h2/` — H2-compatible syntax (local)

Never edit a migration that has already run. Create V6, V7 etc. for changes.

---

## Common Errors and Fixes

| Error | Cause | Fix |
|---|---|---|
| PKIX path building failed | Corporate firewall blocks Maven Central | Use mobile hotspot for `mvn -Paws` builds |
| flyway-database-postgresql version missing | Old pom.xml | Use updated pom.xml from this repo |
| bedrockagentruntime version missing | Not in AWS SDK BOM | Declared explicitly as 2.21.35 in ai-service pom.xml |
| Connection refused port 5432 | RDS unreachable from ECS | Confirm olp-rds-sg allows TCP 5432 from olp-ecs-sg |
| Redis connection refused | ElastiCache not reachable | Confirm olp-redis-sg allows TCP 6379 from olp-ecs-sg |
| AccessDeniedException on Bedrock | Model access not approved | Submit Anthropic usage form in Bedrock console (us-east-1) |
| 403 on ECS image pull | Missing execution role | Create olp-ecs-execution-role with AmazonECSTaskExecutionRolePolicy |
| JWT validation fails on API Gateway | Cognito custom:role missing | Add custom:role as custom attribute in Cognito User Pool |
| LF/CRLF warnings on git push | Windows line endings | Run: git config --global core.autocrlf true |
| 403 on git push | No write access to org repo | Ask org owner to grant Write access to your GitHub account |
