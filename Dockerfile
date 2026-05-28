
# =========================================================
# STAGE 1 - BUILD: compile and package the app with Maven
# =========================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom first and cache dependencies
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn -q clean package -DskipTests

# =========================================================
# STAGE 2 - RUNTIME
# =========================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

# Copy built jar
COPY --from=build /app/target/java-cicd-demo.jar app.jar

EXPOSE 8080

# Healthcheck
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -q --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
