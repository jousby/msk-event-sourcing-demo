package software.amazon.samples.write;

import io.simplesource.api.CommandAPISet;
import io.simplesource.data.Sequence;
import io.simplesource.kafka.api.AggregateSerdes;
import io.simplesource.kafka.api.CommandSerdes;
import io.simplesource.kafka.dsl.EventSourcedApp;
import io.simplesource.kafka.dsl.EventSourcedClient;
import io.simplesource.kafka.serialization.json.JsonAggregateSerdes;
import io.simplesource.kafka.serialization.json.JsonCommandSerdes;
import io.simplesource.kafka.util.PrefixResourceNamingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.samples.write.simplesource.*;

import java.util.Optional;

import static io.simplesource.kafka.serialization.json.JsonGenericMapper.jsonDomainMapper;
import static io.simplesource.kafka.serialization.json.JsonOptionalGenericMapper.jsonOptionalDomainMapper;
import static spark.Spark.*;

public class StartWriteApi {
    private static String KAFKA_GROUP_ID = "demo";
    private static String KAFKA_BOOTSTRAP_SERVERS = "kafka:9092";

    private static final Logger log = LoggerFactory.getLogger(StartWriteApi.class);

    public static void main(String[] args) {
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
                    .withKafkaBootstrap(KAFKA_BOOTSTRAP_SERVERS)
                    .build()
            )
            .<String, AccountCommand>addCommands(builder ->
                builder
                    .withClientId(KAFKA_GROUP_ID)
                    .withCommandResponseRetention(3600L)
                    .withName("account")
                    .withSerdes(ACCOUNT_COMMAND_SERDES)
                    .withResourceNamingStrategy(new PrefixResourceNamingStrategy())
                    .withTopicSpec(1, 1)
            ).build();

        new EventSourcedApp()
            .withKafkaConfig(builder ->
                builder
                    .withKafkaApplicationId("simplesourcing-demo")
                    .withKafkaBootstrap(KAFKA_BOOTSTRAP_SERVERS)
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
        get("/healthcheck", (req, res) -> "OK");

        post("/accounts", (req, res) -> {
            Optional<CreateAccountError> result = writeApi.createAccount(
                req.queryParams("accountName"),
                Double.parseDouble(req.queryParams("openingBalance"))
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
}

