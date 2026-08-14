# Build context must be the repo root (multi-project Gradle build), e.g.:
#   docker build -f services/ingestion-service/Dockerfile -t ingestion-service .
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY services/ingestion-service/build.gradle services/ingestion-service/build.gradle
COPY services/load-generator/build.gradle services/load-generator/build.gradle
RUN ./gradlew :services:ingestion-service:dependencies --no-daemon

COPY services/ingestion-service/src services/ingestion-service/src
RUN ./gradlew :services:ingestion-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/ingestion-service/build/libs/ingestion-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
