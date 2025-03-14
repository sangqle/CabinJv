package examples.sample;

import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class HServer {
    private static final Logger logger = LoggerFactory.getLogger(HServer.class);

    public static void main(String[] args) throws IOException {
        CabinServer server = new ServerBuilder()
                .setPort(8080)
                .enableLogMetrics(false)
                .build();

        Router router = new Router();
        router.get("/", (req, res) -> {
            JsonObject json = new JsonObject();
            json.addProperty("message", "Hello, World!");
            res.send(json);
        });
        server.use(router);
        server.use(AppRouter.router);
        server.start();
        logger.info("Server started at http://localhost:8080");
    }
}
