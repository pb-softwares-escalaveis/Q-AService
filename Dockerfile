FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/QAService-*.jar app.jar
EXPOSE 8098
ENTRYPOINT ["java", "-jar", "app.jar"]
