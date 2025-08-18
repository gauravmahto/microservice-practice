# Dockerfile
# docker build -t practice-app .
# docker run -it --rm --name running-practice-app practice-app
# Above commands build the Docker image and run it interactively and removes the image when done.

# Start from a base image that has Java 17 installed.
# This is much simpler than the one in the real project for now.
FROM openjdk:17-jdk-slim

# Set the working directory inside the container.
WORKDIR /app

# Copy the built .jar file from our local machine into the container.
# The Gradle build process creates this file in the 'build/libs' directory.
COPY build/libs/practice-1.0-SNAPSHOT.jar app.jar

# The command to run when the container starts.
ENTRYPOINT ["java", "-jar", "app.jar"]
