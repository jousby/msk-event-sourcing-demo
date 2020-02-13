package software.amazon.samples.read;

import static spark.Spark.*;

public class StartReadApi {
    public static void main(String[] args) {
        ReadApi readApi = new ElasticsearchReadApi();

        // This automatically starts our http server on port 4567 - Ctl-C to stop
        defineRoutes(readApi);
    }

    public static void defineRoutes(ReadApi readApi) {
        get("/healthcheck", (req, res) -> "OK");

        get("/accounts", (req, res) -> readApi.listAccounts());
        get("/accounts/:id", (req, res) -> readApi.getAccount(req.params("id")));
        get("/accounts/:id/transactions", (req, res) -> readApi.getAccountTransactions(req.params("id")));
    }
}
