# Backend image for Render (or any container host). From this folder:
#   docker build -t wdmmg-api .
#   docker run --env-file .env -p 8080:8080 wdmmg-api

# ---------------------------------------------------------------- build
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencies first, so they're cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Build the jar, then split it into layers (libraries change rarely, our code often)
# so rebuilds and image pulls only move what changed.
RUN mvn -B -q -DskipTests package \
 && cp target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------------------------------------------------------------- run
FROM eclipse-temurin:21-jre

# Run as an unprivileged user.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app

# Render injects PORT; the app reads it (server.port=${PORT:8080}).
ENV PORT=8080
EXPOSE 8080

# Sized for small instances (Render free/starter: 512 MB). Override with JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"

# exec so the JVM receives SIGTERM directly and shuts down gracefully on redeploys.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
