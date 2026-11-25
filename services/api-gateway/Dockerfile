FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY services/pom.xml services/pom.xml
COPY services/ads-service/pom.xml services/ads-service/pom.xml
COPY services/moderation-worker/pom.xml services/moderation-worker/pom.xml
COPY services/ads-query-service/pom.xml services/ads-query-service/pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/api-gateway/src services/api-gateway/src
RUN mvn -f services/pom.xml -pl api-gateway -am -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/services/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar /app/app.jar
USER appuser
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
