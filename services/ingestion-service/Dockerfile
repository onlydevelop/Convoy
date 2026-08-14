# Build context must be the repo root (multi-project Gradle build), e.g.:
#   docker build -f services/ingestion-service/Dockerfile -t ingestion-service .
#
# Uses the official Gradle image (pinned to the wrapper's version) instead of
# the gradlew wrapper, so the build doesn't need to fetch the Gradle
# distribution itself over the network.
FROM gradle:8.10.2-jdk21-alpine AS build
WORKDIR /workspace

COPY settings.gradle build.gradle ./
COPY common/build.gradle common/build.gradle
COPY services/ingestion-service/build.gradle services/ingestion-service/build.gradle
COPY services/load-generator/build.gradle services/load-generator/build.gradle
RUN gradle :services:ingestion-service:dependencies --no-daemon

COPY common/src common/src
COPY services/ingestion-service/src services/ingestion-service/src
RUN gradle :services:ingestion-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/ingestion-service/build/libs/ingestion-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
