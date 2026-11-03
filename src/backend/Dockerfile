# Etapa 1: build
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /src
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd ./
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -q clean package -DskipTests

# Etapa 2: runtime
FROM eclipse-temurin:17-jre

WORKDIR /app
EXPOSE 8080

COPY --from=build /src/target/*.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

ENTRYPOINT ["sh", "/app/docker-entrypoint.sh"]
