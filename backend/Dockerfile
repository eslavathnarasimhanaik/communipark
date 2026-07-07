## Multi-stage build for Spring Boot backend

# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Run with lightweight JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create data directory for H2 (persistent via Render disk)
RUN mkdir -p /app/data

COPY --from=build /app/target/parking-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# Use environment variables for sensitive config
ENV SPRING_DATASOURCE_URL=jdbc:h2:file:/app/data/parkingdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
ENV RAZORPAY_KEY_ID=""
ENV RAZORPAY_KEY_SECRET=""
ENV CASHFREE_APP_ID=""
ENV CASHFREE_SECRET_KEY=""

ENTRYPOINT ["java", "-jar", "app.jar"]
