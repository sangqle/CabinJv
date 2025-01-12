# Stage 1: Build the application
FROM openjdk:17-jdk-slim AS build

# Set the working directory in the container
WORKDIR /app

# Copy the Gradle wrapper files
COPY gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties ./gradlew ./gradlew.bat /app/

# Copy the rest of the project files
COPY . /app

# Make the Gradle wrapper script executable
RUN chmod +x ./gradlew

# Build the application using Gradle
RUN ./gradlew build

# Stage 2: Create the runtime image
FROM openjdk:17-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Copy the built application from the build stage
COPY . /app

# Expose the port the application runs on
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "CabinJ-1.0-SNAPSHOT.jar"]