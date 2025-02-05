package examples;

import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;

import java.io.IOException;

public class HServerSample {
    public static void main(String[] args) throws IOException {
        CabinServer server = new ServerBuilder().setPort(8080).build();
        Router router = new Router();
        router.get("/", (req, res) -> {
            res.writeBody("Hello World");
            res.send();
        });
        server.use(router);
        server.start();
        System.err.println("Server started at http://localhost:8080");
    }
}
