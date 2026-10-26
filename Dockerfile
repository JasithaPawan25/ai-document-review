# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Run stage: small JRE image, non-root user
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --create-home appuser && mkdir -p /app/data/uploads && chown -R appuser /app
USER appuser
COPY --from=build /app/target/ai-document-review-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
