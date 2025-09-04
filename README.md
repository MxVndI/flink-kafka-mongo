## Kafka → Flink → MongoDB

1) Сборка образа приложения
```
docker compose build
```

2) Поднять инфраструктуру (без отправки job)
```
docker compose up -d zookeeper kafka mongo flink-jobmanager flink-taskmanager
```
Проверьте, что Flink UI доступен: http://localhost:8081

3) Отправить Flink job
```
docker compose run --rm submit
```

### Отправить тестовые сообщения в Kafka
```
docker exec -it $(docker ps -qf name=kafka) bash -lc "kafka-console-producer.sh --bootstrap-server kafka:9092 --topic events"
> hello
> world
```

### Проверить записи в MongoDB
```
docker exec -it $(docker ps -qf name=mongo) mongosh --quiet --eval "db.getSiblingDB('flinkdb').events.find().pretty()"
```
Документы имеют вид:
```
{ "payload": "hello", "ts_ingested_ms": 1699999999999 }
```

### Переменные окружения (по умолчанию)
- `KAFKA_BOOTSTRAP`: `kafka:9092`
- `KAFKA_TOPIC`: `events`
- `KAFKA_GROUP_ID`: `flink-mongo-consumer`
- `MONGO_HOST`: `mongo`
- `MONGO_PORT`: `27017`
- `MONGO_DB`: `flinkdb`
- `MONGO_COLLECTION`: `events`

Менять можно через `environment` сервиса `submit` в `docker-compose.yml` или при выполнении:
```
KAFKA_TOPIC=my-topic docker compose run --rm submit
```