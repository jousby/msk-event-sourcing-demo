package software.amazon.samples.read;

import static spark.Spark.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import software.amazon.samples.domain.AccountSummary;

import java.util.List;
import java.util.Optional;

public class StartReadApi {

    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        ReadApi readApi = new ElasticsearchReadApi();

        port(4568);
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

        defineRoutes(readApi);
    }

    public static void defineRoutes(ReadApi readApi) {
        get("/", (req, res) -> "OK");
        get("/healthcheck", (req, res) -> "OK");

        get("/accounts", (req, res) -> {
            List accounts = readApi.list();
            return gson.toJson(accounts);
        });
        get("/accounts/:id", (req, res) -> {
            Optional<AccountSummary> account = readApi.accountSummary(req.params("id"));
            return account.map(a -> gson.toJson(a)).orElse("Not Found");
        });
        get("/accounts/:id/transactions", (req, res) -> {
            List txns = readApi.getTransactions(req.params("id"));
            return gson.toJson(txns);
        });
    }
}
