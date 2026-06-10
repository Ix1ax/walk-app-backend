# ---- Этап сборки ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Сначала кешируем зависимости: копируем только POM и скачиваем их, потом исходники.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Этап запуска ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Запуск под непривилегированным пользователем.
RUN useradd --system --uid 1001 walk
USER walk

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
