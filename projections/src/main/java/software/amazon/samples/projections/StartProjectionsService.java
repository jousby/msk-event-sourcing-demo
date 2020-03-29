package software.amazon.samples.projections;

import static spark.Spark.*;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.http.AWSRequestSigningApacheInterceptor;
import com.amazonaws.services.kafka.AWSKafka;
import com.amazonaws.services.kafka.AWSKafkaClientBuilder;
import com.amazonaws.services.kafka.model.GetBootstrapBrokersRequest;
import com.amazonaws.services.kafka.model.GetBootstrapBrokersResult;
import io.simplesource.kafka.serialization.json.JsonAggregateSerdes;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.kafka.clients.consumer.*;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import io.simplesource.kafka.api.AggregateSerdes;
import io.simplesource.kafka.model.ValueWithSequence;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.samples.write.simplesource.Account;
import software.amazon.samples.write.simplesource.AccountCommand;
import software.amazon.samples.write.simplesource.AccountEvent;

import java.io.IOException;
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

    private boolean isRunning = false;
    AggregateSerdes<String, AccountCommand, AccountEvent, Optional<Account>> ACCOUNT_AGGREGATE_SERDES  =
        new JsonAggregateSerdes<>(
            jsonDomainMapper(),
            jsonDomainMapper(),
            jsonDomainMapper(),
            jsonOptionalDomainMapper());

    public static void main(String[] args) {
        new StartProjectionsService().start();

        // health check
        port(4567);
        get("/", (req, res) -> "OK");
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

        private final RestHighLevelClient esClient;
        private static final String SUMMARY_INDEX = "simplesourcedemo_account_summary";
        private static final String TRANSACTION_INDEX = "simplesourcedemo_account_transaction";

        public EventLogConsummer() {
            String kafkaClusterArn = System.getenv("KAFKA_CLUSTER_ARN");
            log.info("Kafka cluster ARN: " + kafkaClusterArn);

            AWSKafka kafka = AWSKafkaClientBuilder.standard().build();
            GetBootstrapBrokersResult brokersResult = kafka.getBootstrapBrokers(
                new GetBootstrapBrokersRequest().withClusterArn(kafkaClusterArn));

            String kafkaBootstrapBrokers = brokersResult.getBootstrapBrokerString();
            log.info("Bootstrap brokers: " + kafkaBootstrapBrokers);

            kafkaProps = new Properties();
            kafkaProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapBrokers);
            kafkaProps.put(ConsumerConfig.GROUP_ID_CONFIG, KAFKA_GROUP_ID + "projection-service");
            kafkaProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            kafkaProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
            kafkaProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                shutdown.set(true);
            }));


            String elasticsearchUrl = System.getenv("ELASTICSEARCH_URL");
            esClient = esClient(elasticsearchUrl);

            createIndiciesIfNotPresent();

            indexers = List.of(
                new AccountSummaryProjection(esClient),
                new AccountTransactionProjection(esClient)
            );
        }

        // Adds the interceptor to the ES REST client
        private RestHighLevelClient esClient(String elasticsearchUrl) {
            AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
            String serviceName = "es";
            AWS4Signer signer = new AWS4Signer();
            signer.setServiceName(serviceName);
            signer.setRegionName("ap-southeast-2"); // TODO how to inject
            HttpRequestInterceptor interceptor = new AWSRequestSigningApacheInterceptor(serviceName, signer, credentialsProvider);

            return new RestHighLevelClient(RestClient.builder(HttpHost.create("https://" + elasticsearchUrl))
                .setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor)));
        }

        private void createIndiciesIfNotPresent() {
            try {
                boolean summaryIndexExists = esClient.indices().exists(
                    new GetIndexRequest(SUMMARY_INDEX), RequestOptions.DEFAULT);

                if (!summaryIndexExists) createSummaryIndex();

                boolean transactionIndexExists = esClient.indices().exists(
                    new GetIndexRequest(TRANSACTION_INDEX), RequestOptions.DEFAULT);

                if (!transactionIndexExists) createTransactionIndex();
            } catch (IOException e) {
                log.error("Unable to create indicies if not present, this is probably unrecoverable", e);
                throw new RuntimeException(e);
            }
        }

        private void createSummaryIndex() throws IOException {
            CreateIndexRequest request = new CreateIndexRequest(SUMMARY_INDEX);
            request.mapping(
                "{\n" +
                    "  \"properties\": {\n" +
                    "    \"accountName\": {\n" +
                    "      \"type\": \"keyword\"\n" +
                    "    },\n" +
                    "    \"balance\": {\n" +
                    "      \"type\": \"double\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "}",
                XContentType.JSON);

            esClient.indices().create(request, RequestOptions.DEFAULT);
        }

        private void createTransactionIndex() throws IOException {
            CreateIndexRequest request = new CreateIndexRequest(TRANSACTION_INDEX);
            request.mapping(
                "{\n" +
                    "  \"properties\": {\n" +
                    "    \"account\": {\n" +
                    "      \"type\": \"keyword\"\n" +
                    "    },\n" +
                    "    \"amount\": {\n" +
                    "      \"type\": \"double\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "}",
                XContentType.JSON);

            esClient.indices().create(request, RequestOptions.DEFAULT);
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
