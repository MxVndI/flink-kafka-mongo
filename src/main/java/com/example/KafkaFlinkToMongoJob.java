package com.example;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.bson.Document;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

public class KafkaFlinkToMongoJob {
    public static void main(String[] args) throws Exception {
        String kafkaBootstrap = env("KAFKA_BOOTSTRAP", "kafka:9092");
        String kafkaTopic = env("KAFKA_TOPIC", "events");
        String groupId = env("KAFKA_GROUP_ID", "flink-mongo-consumer");

        String mongoHost = env("MONGO_HOST", "mongo");
        int mongoPort = Integer.parseInt(env("MONGO_PORT", "27017"));
        String mongoDb = env("MONGO_DB", "flinkdb");
        String mongoCollection = env("MONGO_COLLECTION", "events");
        String mongoUser = env("MONGO_USER", "");
        String mongoPassword = env("MONGO_PASSWORD", "");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrap)
                .setTopics(kafkaTopic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema(StandardCharsets.UTF_8))
                .build();

        DataStreamSource<String> stream = env.fromSource(
                kafkaSource,
                WatermarkStrategy.noWatermarks(),
                "kafka-source"
        );

        stream.addSink(new MongoSink(mongoHost, mongoPort, mongoDb, mongoCollection, mongoUser, mongoPassword));

        env.execute("Kafka -> Flink -> Mongo");
    }

    private static String env(String key, String def) {
        return Optional.ofNullable(System.getenv(key)).filter(s -> !s.isEmpty()).orElse(def);
    }

    public static class MongoSink implements SinkFunction<String> {
        private final String host;
        private final int port;
        private final String databaseName;
        private final String collectionName;
        private final String user;
        private final String password;

        private transient MongoClient client;
        private transient MongoCollection<Document> collection;

        public MongoSink(String host, int port, String databaseName, String collectionName, String user, String password) {
            this.host = host;
            this.port = port;
            this.databaseName = databaseName;
            this.collectionName = collectionName;
            this.user = user;
            this.password = password;
        }

        @Override
        public void invoke(String value, Context context) {
            ensureClient();
            Document doc = new Document()
                    .append("payload", value)
                    .append("ts_ingested_ms", System.currentTimeMillis());
            collection.insertOne(doc);
        }

        private void ensureClient() {
            if (client != null && collection != null) {
                return;
            }

            MongoClientSettings.Builder builder = MongoClientSettings.builder()
                    .applyToClusterSettings(cs -> cs.hosts(Collections.singletonList(new ServerAddress(host, port))));

            if (user != null && !user.isEmpty()) {
                MongoCredential credential = MongoCredential.createCredential(user, databaseName, password.toCharArray());
                builder.credential(credential);
            }

            client = MongoClients.create(builder.build());
            MongoDatabase db = client.getDatabase(databaseName);
            collection = db.getCollection(collectionName);
        }
    }
}


