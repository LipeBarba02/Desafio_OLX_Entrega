FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY src src
RUN mvn -f pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/target/olx-simple-1.0.0.jar /app/app.jar
USER appuser
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
