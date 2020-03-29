package software.amazon.samples.write;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.kafka.AWSKafka;
import com.amazonaws.services.kafka.AWSKafkaClientBuilder;
import com.amazonaws.services.kafka.model.DescribeClusterRequest;
import com.amazonaws.services.kafka.model.GetBootstrapBrokersRequest;
import com.amazonaws.services.kafka.model.GetBootstrapBrokersResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.simplesource.api.CommandAPISet;
import io.simplesource.data.Sequence;
import io.simplesource.kafka.api.AggregateSerdes;
import io.simplesource.kafka.api.CommandSerdes;
import io.simplesource.kafka.dsl.EventSourcedApp;
import io.simplesource.kafka.dsl.EventSourcedClient;
import io.simplesource.kafka.serialization.json.JsonAggregateSerdes;
import io.simplesource.kafka.serialization.json.JsonCommandSerdes;
import io.simplesource.kafka.util.PrefixResourceNamingStrategy;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.samples.write.simplesource.*;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.simplesource.kafka.serialization.json.JsonGenericMapper.jsonDomainMapper;
import static io.simplesource.kafka.serialization.json.JsonOptionalGenericMapper.jsonOptionalDomainMapper;
import static spark.Spark.*;

public class StartWriteApi {
    private static String KAFKA_GROUP_ID = "demo";

    private static final Logger log = LoggerFactory.getLogger(StartWriteApi.class);

    public static void main(String[] args) {
        String kafkaClusterArn = System.getenv("KAFKA_CLUSTER_ARN");
        log.info("Kafka cluster ARN: " + kafkaClusterArn);

        AWSKafka kafka = AWSKafkaClientBuilder.standard().build();
        GetBootstrapBrokersResult brokersResult = kafka.getBootstrapBrokers(
            new GetBootstrapBrokersRequest().withClusterArn(kafkaClusterArn));

        String kafkaBootstrapBrokers = brokersResult.getBootstrapBrokerString();
        log.info("Bootstrap brokers: " + kafkaBootstrapBrokers);

        AggregateSerdes<String, AccountCommand, AccountEvent, Optional<Account>> ACCOUNT_AGGREGATE_SERDES  =
            new JsonAggregateSerdes<>(
                jsonDomainMapper(),
                jsonDomainMapper(),
                jsonDomainMapper(),
                jsonOptionalDomainMapper());

        CommandSerdes<String, AccountCommand> ACCOUNT_COMMAND_SERDES =
            new JsonCommandSerdes<>(jsonDomainMapper(), jsonDomainMapper());

        CommandAPISet result = new EventSourcedClient()
            .withKafkaConfig(builder ->
                builder
                    .withKafkaApplicationId(KAFKA_GROUP_ID)
                    .withKafkaBootstrap(kafkaBootstrapBrokers)
                    .build()
            )
            .<String, AccountCommand>addCommands(builder ->
                builder
                    .withClientId(KAFKA_GROUP_ID)
                    .withCommandResponseRetention(3600L)
                    .withName("account")
                    .withSerdes(ACCOUNT_COMMAND_SERDES)
                    .withResourceNamingStrategy(new PrefixResourceNamingStrategy())
                    .withTopicSpec(9, 3)
            ).build();

        new EventSourcedApp()
            .withKafkaConfig(builder ->
                builder
                    .withKafkaApplicationId("simplesourcing-demo")
                    .withKafkaBootstrap(kafkaBootstrapBrokers)
                    .build()
            )
            .<String, AccountCommand, AccountEvent, Optional<Account>>addAggregate(aggregateBuilder ->
                aggregateBuilder
                    .withName("account")
                    .withSerdes(ACCOUNT_AGGREGATE_SERDES)
                    .withInitialValue(k -> Optional.empty())
                    .withAggregator(AccountAggregator.getInstance())
                    .withCommandHandler(AccountCommandHandler.getInstance())
                    .withResourceNamingStrategy(new PrefixResourceNamingStrategy())
                    .build()
            )
            .start();

        WriteApi writeApi = new SimplesourceWriteApi(result.getCommandAPI("account"));
        port(4567);
        options("/*",

            (request, response) -> {

                String accessControlRequestHeaders = request
                    .headers("Access-Control-Request-Headers");
                if (accessControlRequestHeaders != null) {
                    response.header("Access-Control-Allow-Headers",
                        accessControlRequestHeaders);
                }

                String accessControlRequestMethod = request
                    .headers("Access-Control-Request-Method");
                if (accessControlRequestMethod != null) {
                    response.header("Access-Control-Allow-Methods",
                        accessControlRequestMethod);
                }

                return "OK";
            });
        before((request, response) -> response.header("Access-Control-Allow-Origin", "*"));
        defineRoutes(writeApi);
    }

    public static void defineRoutes(WriteApi writeApi) {
        get("/", (req, res) -> "OK");

        post("/accounts", (req, res) -> {
            Map<String, String> params = toMap(req.body());

            String accountName = getUnsafeValue("accountName", params);
            String openingBalance = getUnsafeValue("openingBalance", params);

            Optional<CreateAccountError> result = writeApi.createAccount(
                accountName,
                Double.parseDouble(openingBalance)
            );

            return result.map(e -> e.message()).orElse("OK");
        });

        post("/accounts/:id/deposit", (req, res) -> {
            writeApi.deposit(
                req.params("id"),
                Double.parseDouble(req.queryParams("amount")),
                Sequence.position(Long.parseLong(req.queryParams("version")))
            );

            return "OK";
        });

        post("/accounts/:id/withdraw", (req, res) -> {
            writeApi.withdraw(
                req.params("id"),
                Double.parseDouble(req.queryParams("amount")),
                Sequence.position(Long.parseLong(req.queryParams("version")))
            );

            return "OK";
        });
    }

    private static Map<String, String> toMap(String jsonString) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonString, new TypeReference<Map<String, String>>(){});
    }

    private static String getUnsafeValue(String paramName, Map<String, String> params) throws Exception {
        String value = params.get(paramName);
        if (value == null) {
            throw new Exception("The following parameter was not present in the request: " + paramName);
        }
        return value;
    }
}

