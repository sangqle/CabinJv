# Stage 1: Build the application
FROM openjdk:17-jdk-slim AS build

# Set the working directory in the container
WORKDIR /app

# Copy only Gradle wrapper and configuration files first (cacheable step)
COPY gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties ./gradlew ./gradlew.bat /app/

# Make the Gradle wrapper script executable
RUN chmod +x ./gradlew

# Copy `build.gradle` and `settings.gradle` first (cacheable step)
COPY build.gradle settings.gradle /app/

# Pre-download Gradle dependencies (cacheable step)
RUN ./gradlew dependencies --no-daemon || return 0

# Copy the rest of the project files
COPY . /app

# Build the application and copy dependencies
RUN ./gradlew build --no-daemon

# Stage 2: Create the runtime image
FROM openjdk:17-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Copy the built application JAR and dependencies from the build stage
COPY --from=build /app/build/libs/cabin-1.0.1.jar /app/
COPY --from=build /app/build/libs/libs /app/libs

# Expose the port the application runs on
EXPOSE 8080

# Run the application
CMD ["java", "-cp", "cabin-1.0.1.jar:libs/*", "com.cabin.Main"]