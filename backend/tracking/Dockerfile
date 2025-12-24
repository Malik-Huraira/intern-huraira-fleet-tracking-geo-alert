# Stage 1: Build the application
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and source code
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src ./src

# Make the Maven wrapper executable and build the project
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install required libraries for RocksDB (Kafka Streams state store) and cleanup tools
RUN apk add --no-cache libstdc++ bash curl

# Create a cleanup script for Kafka Streams state directory
RUN echo '#!/bin/bash' > /app/cleanup-streams.sh && \
    echo 'echo "🧹 Cleaning Kafka Streams state directory..."' >> /app/cleanup-streams.sh && \
    echo 'rm -rf /tmp/kafka-streams/* 2>/dev/null || true' >> /app/cleanup-streams.sh && \
    echo 'echo "✅ Cleanup complete"' >> /app/cleanup-streams.sh && \
    chmod +x /app/cleanup-streams.sh

# Create health check script
RUN echo '#!/bin/bash' > /app/healthcheck.sh && \
    echo 'curl -f http://localhost:8080/actuator/health || exit 1' >> /app/healthcheck.sh && \
    chmod +x /app/healthcheck.sh

# Copy the built jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Create a non-root user to run the application
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

EXPOSE 8080

# Run cleanup, then start the application
CMD ["sh", "-c", "/app/cleanup-streams.sh && java -jar app.jar"]