# syntax=docker/dockerfile:1

# Stage 1: build the jar using an image that has Maven and Java 21.
FROM maven:3.9-eclipse-temurin-21 AS build
# Work inside /workspace.
WORKDIR /workspace
# Copy the Maven project file first, so downloaded libraries are cached between builds.
COPY pom.xml .
# Download all libraries (kept in a build cache).
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
# Copy the source code.
COPY src ./src
# Compile and package the app into a jar.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package

# Stage 2: a small image that only has Java, to run the jar.
FROM eclipse-temurin:21-jre-alpine
# Create a normal (non-root) user to run the app.
RUN addgroup -S app && adduser -S app -G app
# Work inside /app.
WORKDIR /app
# Copy the jar from stage 1.
COPY --from=build /workspace/target/emis-analytics-api-*.jar app.jar
# Run as the normal user.
USER app
# The app listens on port 8080.
EXPOSE 8080
# Start the app.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
