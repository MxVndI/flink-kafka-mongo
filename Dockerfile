# syntax=docker/dockerfile:1

# --- Build stage: package shaded jar ---
FROM maven:3.9.6-eclipse-temurin-11 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -e -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -e -B -DskipTests package

# --- Runtime stage: use Flink image and add jar ---
FROM flink:1.17.2-scala_2.12-java11
USER root
RUN mkdir -p /opt/flink/usrlib && chown -R flink:flink /opt/flink/usrlib
COPY --from=build /workspace/target/flink-kafka-mongo.jar /opt/flink/usrlib/app.jar
USER flink

# Default command does nothing; compose will override for submitter
CMD ["bash", "-lc", "ls -l /opt/flink/usrlib && echo 'Built app image' && tail -f /dev/null"]


