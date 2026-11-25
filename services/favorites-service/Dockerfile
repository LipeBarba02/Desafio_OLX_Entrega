FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY services/pom.xml services/pom.xml
COPY services/ads-service/pom.xml services/ads-service/pom.xml
COPY services/favorites-service/pom.xml services/favorites-service/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/favorites-service/src services/favorites-service/src
RUN mvn -f services/pom.xml -pl favorites-service -am -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/services/favorites-service/target/favorites-service-1.0.0-SNAPSHOT.jar /app/app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
