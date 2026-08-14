# Build context must be the repo root (multi-project Gradle build), e.g.:
#   docker build -f services/processing-service/Dockerfile -t processing-service .
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
COPY services/processing-service/build.gradle services/processing-service/build.gradle
RUN gradle :services:processing-service:dependencies --no-daemon

COPY common/src common/src
COPY services/processing-service/src services/processing-service/src
RUN gradle :services:processing-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/processing-service/build/libs/processing-service.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
