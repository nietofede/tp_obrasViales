# syntax=docker/dockerfile:1

# ---------- Etapa 1: compilar con Maven (Java 25) ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl api-gateway -am package -DskipTests

# ---------- Etapa 2: imagen final, solo con el JRE ----------
FROM eclipse-temurin:25-jre
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=build /build/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]