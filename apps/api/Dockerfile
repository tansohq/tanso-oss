# syntax=docker/dockerfile:1

########################
# 1) Build (Maven)
########################
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1a. Pre-cache dependencies (faster incremental builds)
COPY pom.xml ./
RUN mvn -q -B -e -DskipTests dependency:go-offline

# 1b. Copy sources and build
COPY src ./src
# Produce a consistent JAR name (app.jar) regardless of artifactId/version
RUN mvn -q -B -DskipTests -DfinalName=app clean package

########################
# 2) Runtime (JRE only)
########################
FROM eclipse-temurin:21-jre
# (Optional) run as non-root
RUN useradd -r -u 1001 app && mkdir -p /app && chown -R app:app /app
USER app

WORKDIR /app
COPY --from=build /workspace/target/app.jar /app/app.jar

# Reasonable defaults for containerized JVMs
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"

# Default to the hardened "docker" profile (application-docker.yaml) so a bare `docker run`
# doesn't fall back to the insecure base application.yaml. Override with SPRING_PROFILES_ACTIVE.
ENV SPRING_PROFILES_ACTIVE=docker

# Health/port hints (adjust if not 8080)
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]
