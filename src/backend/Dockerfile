# Etapa 1: build
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /src
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd ./
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -q clean package -DskipTests

# Runtime compartilhado pelos builds de fonte e pelo JAR já ensaiado na CI.
FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app
EXPOSE 8080

COPY docker-entrypoint.sh /app/docker-entrypoint.sh

ENTRYPOINT ["sh", "/app/docker-entrypoint.sh"]

# BuildKit não executa a etapa Maven para este target independente.
FROM runtime AS tested-jar
ARG JAR_SHA256
ARG SOURCE_SHA
COPY target/*.jar /app/app.jar
RUN test -n "$JAR_SHA256" \
    && printf '%s  /app/app.jar\n' "$JAR_SHA256" | sha256sum -c -
LABEL org.opencontainers.image.revision="$SOURCE_SHA" \
      dev.vanrides.jar-sha256="$JAR_SHA256"

# O build padrão por fontes continua disponível para os consumidores existentes.
FROM runtime AS final
COPY --from=build /src/target/*.jar /app/app.jar
