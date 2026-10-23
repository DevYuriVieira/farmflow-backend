FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S farmflow && adduser -S farmflow -G farmflow

COPY --from=builder --chown=farmflow:farmflow /app/target/*.jar app.jar

USER farmflow:farmflow

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/v1/health/live || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]