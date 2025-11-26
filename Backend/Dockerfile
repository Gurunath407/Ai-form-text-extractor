# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jdk
WORKDIR /usr/app/
COPY --from=build /app/target/Ai-Handwritten-Form-Text-Extractor-V1.jar /usr/app/
COPY .env /usr/app/.env
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar Ai-Handwritten-Form-Text-Extractor-V1.jar"]
