package software.amazon.samples.write;

import static spark.Spark.*;

public class StartWriteApi {
    public static void main(String[] args) {
        SimpleSourcingWriteApi writeApi = new SimpleSourcingWriteApi();

        defineRoutes(writeApi);
    }

    public static void defineRoutes(WriteApi writeApi) {
        get("/healthcheck", (req, res) -> "OK");

        post("/accounts", (req, res) -> {
            writeApi.createAccount(
                req.params("accountName"),
                Double.parseDouble(req.params("openingBalance")));
            return "OK";
        });

    }
}

