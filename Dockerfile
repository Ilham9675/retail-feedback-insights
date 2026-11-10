# syntax=docker/dockerfile:1

# ---- Build: compile and package the web app (the CoreNLP module is not needed at runtime) ----
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY sentiment-core/pom.xml sentiment-core/
COPY web-app/pom.xml web-app/
# Dependencies first, so code changes don't download them again
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl web-app dependency:go-offline
COPY web-app/src web-app/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl web-app package -DskipTests

# ---- Run: JRE only, non-root user ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 app \
    && mkdir -p /app/data/db \
    && chown -R app /app/data
# Sample dataset imported on first start; reviews and AI results live in the database
COPY --chown=app data/store_feedback.txt data/sentiment_feedback_output.txt data/
COPY --from=build /src/web-app/target/web-app-*.jar app.jar
USER app

ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
