FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY flagship-demo-app/target/flagship-demo-app-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
