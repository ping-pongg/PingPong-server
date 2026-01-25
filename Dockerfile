FROM openjdk:17.0.1-jdk-slim
WORKDIR /app
COPY ./build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]