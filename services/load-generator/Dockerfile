# Build context must be the repo root (multi-project Gradle build), e.g.:
#   docker build -f services/load-generator/Dockerfile -t load-generator .
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY services/ingestion-service/build.gradle services/ingestion-service/build.gradle
COPY services/load-generator/build.gradle services/load-generator/build.gradle
RUN ./gradlew :services:load-generator:dependencies --no-daemon

COPY services/load-generator/src services/load-generator/src
RUN ./gradlew :services:load-generator:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/load-generator/build/libs/load-generator.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
