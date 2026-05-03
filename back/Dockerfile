# Étape 1 : build avec Gradle
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon -q 2>/dev/null || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test -q

# Étape 2 : runtime léger
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S ycyw && adduser -S ycyw -G ycyw
USER ycyw

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]