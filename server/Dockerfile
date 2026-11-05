# syntax=docker/dockerfile:1.7

# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS build

# shadowJar ends with the fat JAR smoke, which starts an embedded Postgres.
# initdb refuses to run as root, and Docker's seccomp profile blocks the
# user-namespace workaround, so the build runs as an ordinary user.
RUN useradd -m builder && mkdir /workspace && chown builder:builder /workspace
USER builder
WORKDIR /workspace

# Cache Gradle dependencies separately from sources for faster rebuilds
COPY --chown=builder:builder gradle gradle
COPY --chown=builder:builder gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY --chown=builder:builder server/build.gradle.kts server/
COPY --chown=builder:builder shared/build.gradle.kts shared/
COPY --chown=builder:builder composeApp/build.gradle.kts composeApp/
RUN chmod +x gradlew && \
    ./gradlew --no-daemon :server:dependencies > /dev/null 2>&1 || true

# Build the fat JAR
COPY --chown=builder:builder server server
COPY --chown=builder:builder shared shared
RUN ./gradlew --no-daemon :server:shadowJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

RUN groupadd -r app && useradd -r -g app app && \
    mkdir -p /app && chown -R app:app /app
USER app

COPY --from=build /workspace/server/build/libs/*-all.jar /app/server.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Hosts that run this image without docker-compose.yml (Fly, Render, Railway)
# fail closed too: no JWT_SECRET, no start.
ENV APP_ENV=production
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/server.jar"]
