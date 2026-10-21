# syntax=docker/dockerfile:1

# ---- build stage: produces target/flowboard.jar with the production frontend bundle
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---- runtime stage: slim JRE, non-root user, persistent data volume
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --home-dir /app flowboard
WORKDIR /app
COPY --from=build /workspace/target/flowboard.jar ./flowboard.jar
RUN mkdir -p /app/data && chown -R flowboard:flowboard /app
USER flowboard
VOLUME ["/app/data"]
ENV SPRING_DATASOURCE_URL=jdbc:h2:file:/app/data/flowboard
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/flowboard.jar"]
