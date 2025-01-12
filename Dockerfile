# Use an official OpenJDK runtime as a parent image
FROM openjdk:17-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Copy the rest of the project files
COPY . /app

# Make the Gradle wrapper script executable
RUN chmod +x ./gradlew

# Build the application using Gradle
RUN ./gradlew build

# Expose the port the application runs on
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "build/libs/CabinJ-1.0-SNAPSHOT.jar"]