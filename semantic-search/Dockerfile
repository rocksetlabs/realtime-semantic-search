FROM maven:3.8.6-openjdk-11 AS builder
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml clean package

FROM openjdk:11.0.2-jdk-slim
COPY --from=builder /usr/src/app/target/semantic-search-1.0.0.jar /usr/app/semantic-search-1.0.0.jar

CMD ["java", "-cp", "/usr/app/semantic-search-1.0.0.jar", "com.github.zacharydhamilton.App"]