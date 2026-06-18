# Online Learning Portal (OLP)

Cloud-native LMS — Java 17 Spring Boot microservices + React 18 + AWS Bedrock Gen AI.

---

## Folder Structure

```
online-learning-portal/          ← this is the root folder (push this to Git)
├── README.md                    ← this file
├── .gitignore
│
├── olp_code/                    ← Spring Boot backend (Maven multi-module)
│   ├── pom.xml                  ← parent POM
│   ├── README.md                ← full backend + deployment guide
│   ├── PROMPTS.md               ← AI generation prompts
│   ├── deploy.sh                ← push to ECR + deploy to ECS
│   ├── docker-compose.yml       ← run all services locally
│   ├── common/                  ← shared library
│   ├── auth-service/            ← port 8081
│   ├── course-service/          ← port 8082
│   ├── enrollment-service/      ← port 8083
│   ├── progress-service/        ← port 8084
│   └── ai-service/              ← port 8085
│
└── olp_frontend/                ← React 18 SPA
    ├── package.json
    ├── vite.config.ts
    └── src/
```

---

## Quick Start — Local Development

### Backend

```bash
cd olp_code
set SPRING_PROFILES_ACTIVE=local     # Windows CMD
# $env:SPRING_PROFILES_ACTIVE="local"  # PowerShell

mvn clean install -DskipTests
mvn spring-boot:run -pl auth-service
```

### Frontend

```bash
cd olp_frontend
npm install
npm run dev
# Opens at http://localhost:5173
```

### Test accounts (auto-created on startup)

| Role | Email | Password |
|---|---|---|
| Learner | learner@olp.com | Test1234! |
| Instructor | instructor@olp.com | Test1234! |

---

## Full Documentation

See `olp_code/README.md` for:
- Complete folder structure
- All service endpoints
- AWS infrastructure values (VPC, RDS, Redis, etc.)
- Step-by-step AWS deployment guide
- Environment variables for ECS
- Common errors and fixes
