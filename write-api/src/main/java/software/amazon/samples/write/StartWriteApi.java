package software.amazon.samples.write;

import static spark.Spark.*;

public class StartWriteApi {
    public static void main(String[] args) {
        get("/healthcheck", (req, res) -> "OK");
    }
}

