FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY flagship-server/target/flagship-server-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
