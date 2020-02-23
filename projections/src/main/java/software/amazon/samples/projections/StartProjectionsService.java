package software.amazon.samples.projections;

import io.simplesource.kafka.serialization.json.JsonAggregateSerdes;
import org.apache.http.HttpHost;
import org.apache.kafka.clients.consumer.*;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import io.simplesource.kafka.api.AggregateSerdes;
import io.simplesource.kafka.model.ValueWithSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.samples.write.simplesource.Account;
import software.amazon.samples.write.simplesource.AccountCommand;
import software.amazon.samples.write.simplesource.AccountEvent;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.simplesource.kafka.serialization.json.JsonGenericMapper.jsonDomainMapper;
import static io.simplesource.kafka.serialization.json.JsonOptionalGenericMapper.jsonOptionalDomainMapper;


public class StartProjectionsService {
    private static final Duration POLL_DURATION = Duration.ofSeconds(30);

    private static String KAFKA_GROUP_ID = "demo";
    private static String KAFKA_BOOTSTRAP_SERVERS = "kafka:9092";

    private static String ELASTICSEARCH_HOST = "elasticsearch";
    private static int ELASTICSEARCH_PORT = 9200;

    private boolean isRunning = false;
    AggregateSerdes<String, AccountCommand, AccountEvent, Optional<Account>> ACCOUNT_AGGREGATE_SERDES  =
        new JsonAggregateSerdes<>(
            jsonDomainMapper(),
            jsonDomainMapper(),
            jsonDomainMapper(),
            jsonOptionalDomainMapper());

    public static void main(String[] args) {
        new StartProjectionsService().start();
    }

    public void start() {
        if (!isRunning) {
            Thread consumer = new Thread(new EventLogConsummer());
            consumer.setPriority(Thread.MIN_PRIORITY);
            consumer.start();
            isRunning = true;
        }
    }

    private final class EventLogConsummer implements Runnable {
        private final Properties kafkaProps;

        private AtomicBoolean shutdown = new AtomicBoolean(false);

        private final Logger log = LoggerFactory.getLogger(EventLogConsummer.class);

        private final List<Indexer> indexers;

        public EventLogConsummer() {
            kafkaProps = new Properties();
            kafkaProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
            kafkaProps.put(ConsumerConfig.GROUP_ID_CONFIG, KAFKA_GROUP_ID + "projection-service");
            kafkaProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            kafkaProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
            kafkaProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                shutdown.set(true);
            }));


            RestHighLevelClient esClient = new RestHighLevelClient(
                RestClient.builder(
                    new HttpHost(ELASTICSEARCH_HOST, ELASTICSEARCH_PORT, "http")));

            indexers = List.of(
                new AccountSummaryProjection(esClient),
                new AccountTransactionProjection(esClient)
            );
        }

        @Override
        public void run() {
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaProps);

            log.info("****************************************");
            log.info("Started Elasticsearch projection service");
            log.info("****************************************");

            consumer.subscribe(Collections.singleton("account-event"));

            // Naive implementation, no error handling, will die on first exception. TODO
            while (!shutdown.get()) {
                ConsumerRecords<String, String> records = consumer.poll(POLL_DURATION);
                records.forEach(record -> {
                    ValueWithSequence<AccountEvent> event = ACCOUNT_AGGREGATE_SERDES
                        .valueWithSequence()
                        .deserializer()
                        .deserialize(record.topic(), record.value().getBytes());
                    String key = ACCOUNT_AGGREGATE_SERDES
                        .aggregateKey()
                        .deserializer()
                        .deserialize(record.topic(), record.key().getBytes());
                    indexers.stream().forEach(indexer -> indexer.index(key, event));
                });
                consumer.commitSync();
            }

            consumer.commitSync();
            consumer.close(Duration.ofSeconds(60));
        }
    }
}
