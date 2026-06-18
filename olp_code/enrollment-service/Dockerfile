# ── Stage 1: Build ────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy parent POM and all module POMs first (layer caching)
COPY pom.xml .
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/
COPY course-service/pom.xml course-service/
COPY enrollment-service/pom.xml enrollment-service/
COPY progress-service/pom.xml progress-service/
COPY ai-service/pom.xml ai-service/

# Download dependencies (cached as a layer if POMs don't change)
RUN mvn dependency:go-offline -Paws -q

# Copy all source code
COPY common/src common/src
COPY auth-service/src auth-service/src
COPY course-service/src course-service/src
COPY enrollment-service/src enrollment-service/src
COPY progress-service/src progress-service/src
COPY ai-service/src ai-service/src

# Build only this service (with aws profile for full AWS SDK)
RUN mvn clean package -pl enrollment-service -am -DskipTests -Paws

# ── Stage 2: Runtime ──────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S olp && adduser -S olp -G olp

# Copy the built JAR
COPY --from=build /workspace/enrollment-service/target/*.jar app.jar

# Set ownership
RUN chown olp:olp app.jar

USER olp

EXPOSE 8083

# JVM tuning for containers:
# -XX:+UseContainerSupport  → respects ECS task memory limits
# -XX:MaxRAMPercentage=75   → uses 75% of container memory for heap
ENTRYPOINT ["java",   "-XX:+UseContainerSupport",   "-XX:MaxRAMPercentage=75",   "-Djava.security.egd=file:/dev/./urandom",   "-jar", "app.jar"]
