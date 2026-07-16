FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# corre como utilizador não-root (hardening; a porta 8080 não é privilegiada)
RUN addgroup -S tracky && adduser -S tracky -G tracky
USER tracky
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
