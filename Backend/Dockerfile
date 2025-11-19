# Use Eclipse Temurin JDK 21
FROM eclipse-temurin:21-jdk

# Set working directory
WORKDIR /usr/app/

# Copy the Spring Boot JAR
COPY target/Ai-Handwritten-Form-Text-Extractor-V1.jar /usr/app/

# Copy the .env file into the container
COPY .env /usr/app/.env

# Expose port
EXPOSE 8080

# Use ENTRYPOINT with sh -c so environment variables from docker-compose or .env are picked up
ENTRYPOINT ["sh", "-c", "java -jar Ai-Handwritten-Form-Text-Extractor-V1.jar"]
