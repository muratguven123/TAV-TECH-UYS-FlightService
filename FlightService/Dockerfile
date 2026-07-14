# ── Stage 1: Build ───────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Build and install the shared library uys-events-contract
COPY uys-events-contract ./uys-events-contract
RUN mvn -f uys-events-contract/pom.xml clean install -DskipTests -q

# Build the FlightService
COPY FlightService/FlightService/pom.xml ./FlightService/FlightService/
COPY FlightService/FlightService/src ./FlightService/FlightService/src
RUN mvn -f FlightService/FlightService/pom.xml package -Dmaven.test.skip=true -q

# ── Stage 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /app/FlightService/FlightService/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
