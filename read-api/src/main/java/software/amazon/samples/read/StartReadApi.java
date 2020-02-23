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

        // This automatically starts our http server on port 4568 - Ctl-C to stop
        port(4568);
        defineRoutes(readApi);
    }

    public static void defineRoutes(ReadApi readApi) {
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
