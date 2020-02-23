package software.amazon.samples.read;

import static spark.Spark.*;

public class StartReadApi {
    public static void main(String[] args) {
        ReadApi readApi = new ElasticsearchReadApi();

        // This automatically starts our http server on port 4567 - Ctl-C to stop
        port(4568);
        defineRoutes(readApi);
    }

    public static void defineRoutes(ReadApi readApi) {
        get("/healthcheck", (req, res) -> "OK");

        get("/accounts", (req, res) -> readApi.list());
        get("/accounts/:id", (req, res) -> readApi.accountSummary(req.params("id")));
        get("/accounts/:id/transactions", (req, res) -> readApi.getTransactions(req.params("id")));
    }
}
