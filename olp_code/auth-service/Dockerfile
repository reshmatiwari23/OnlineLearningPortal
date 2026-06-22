FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY course-service/pom.xml course-service/pom.xml
COPY enrollment-service/pom.xml enrollment-service/pom.xml
COPY progress-service/pom.xml progress-service/pom.xml
COPY ai-service/pom.xml ai-service/pom.xml
RUN mvn dependency:go-offline -Paws -q
COPY common/src common/src
COPY auth-service/src auth-service/src
RUN mvn clean package -Paws -DskipTests -pl common,auth-service --no-transfer-progress

FROM public.ecr.aws/amazoncorretto/amazoncorretto:17-alpine
WORKDIR /app
RUN addgroup -S olp && adduser -S olp -G olp
USER olp
COPY --from=build /app/auth-service/target/auth-service-*.jar app.jar
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1
EXPOSE 8081
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-XX:+UseContainerSupport","-jar","app.jar"]
