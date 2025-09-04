# Build stage
FROM maven:3.8.6-openjdk-11 AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# Runtime stage
FROM flink:1.17.2-scala_2.12-java11
RUN mkdir -p /opt/flink/usrlib && chown -R flink:flink /opt/flink/usrlib
COPY --from=builder /workspace/target/flink-kafka-mongo.jar /opt/flink/usrlib/app.jar