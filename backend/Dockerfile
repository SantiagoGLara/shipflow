
# Etapa de compilación
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests -B

# Etapa de ejecución
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 10001 spring

COPY --from=build /app/target/*.jar app.jar

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
