# One Dockerfile for every service:
#   docker build --build-arg SERVICE=queue-service -t queue-service .
FROM maven:3.9.6-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl "${SERVICE}" -am -DskipTests package \
    && cp "${SERVICE}"/target/*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
ENV LOG_PATH=/tmp/logs \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
