# Dockerfile
# docker build -t practice-app .
# docker run -it --rm --name running-practice-app practice-app
# Above commands build the Docker image and run it interactively and removes the image when done.

# ---------- Build stage ----------
FROM gradle:8.10.1-jdk17-jammy AS build
WORKDIR /workspace

# Copy build scripts and sources
COPY build.gradle ./
COPY settings.gradle ./
COPY src ./src

# Build the application (skip tests for speed)
RUN gradle --no-daemon clean build -x test

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy only the runnable jar from the build stage
# Copy only the runnable fat jar from the build stage
COPY --from=build /workspace/build/libs/*-all.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
