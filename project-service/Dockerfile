FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /workspace/target/project-service-*.jar app.jar

USER spring

EXPOSE 8086

ENV SERVER_PORT=8086

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
