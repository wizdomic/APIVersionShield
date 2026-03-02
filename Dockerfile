# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy maven wrapper and pom first (layer caching)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -q

# Copy source and build
COPY src src
RUN ./mvnw package -DskipTests -q

# ---- Run Stage ----
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S apiguard && adduser -S apiguard -G apiguard

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Own the jar
RUN chown apiguard:apiguard app.jar

USER apiguard

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]