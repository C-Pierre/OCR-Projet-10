# Étape 1 : build
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# Étape 2 : runtime
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S ycyw && adduser -S ycyw -G ycyw
USER ycyw

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]