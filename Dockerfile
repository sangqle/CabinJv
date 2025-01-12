# Stage 1: Build the application
FROM openjdk:17-jdk-slim AS build

# Set the working directory in the container
WORKDIR /app

# Copy the necessary Gradle files and scripts
COPY gradle/wrapper/gradle-wrapper.properties /app/gradle/wrapper/
COPY build.gradle settings.gradle /app/
COPY src /app/src

# Create and configure the Gradle wrapper
RUN mkdir -p gradle/wrapper
RUN apt-get update && apt-get install -y wget \
    && wget https://services.gradle.org/distributions/gradle-7.6-bin.zip -P /app/gradle/wrapper \
    && unzip -d /app/gradle/wrapper /app/gradle/wrapper/gradle-7.6-bin.zip \
    && chmod +x /app/gradle/wrapper/gradle-wrapper.jar

# Run the Gradle wrapper to build the application
RUN /app/gradle/wrapper/gradlew build

# Stage 2: Create the runtime image
FROM openjdk:17-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Copy the built application from the build stage
COPY --from=build /app/build/libs/*.jar /app/

# Expose the port the application runs on
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "CabinJ-1.0-SNAPSHOT.jar"]