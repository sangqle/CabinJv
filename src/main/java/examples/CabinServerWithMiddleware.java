package examples;

import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Middleware;
import com.cabin.express.interfaces.ServerLifecycleCallback;
import com.cabin.express.loggger.CabinLogger;
import com.cabin.express.middleware.MiddlewareChain;
import com.cabin.express.router.Router;
import com.cabin.express.server.CabinServer;
import com.cabin.express.server.ServerBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

class ServerCallBack implements ServerLifecycleCallback {
    @Override
    public void onServerInitialized(int port) {
        System.err.println("Server started on port: " + port);
    }

    @Override
    public void onServerStopped() {
        System.err.println("Server stopped");
    }

    @Override
    public void onServerFailed(Exception e) {
        System.err.println("Server failed to start: " + e.getMessage());
    }
}

class JwtMiddleware implements Middleware {
    static CabinLogger.LoggerInstance _Logger = CabinLogger.getLogger(JwtMiddleware.class);

    @Override
    public void apply(Request request, Response response, MiddlewareChain next) throws IOException {
        String jwtToken = request.getHeader("Authorization");
        if (jwtToken == null || !jwtToken.startsWith("Bearer ")) {
            _Logger.warn("JWT token is missing or invalid");
            response.setStatusCode(401); response.writeBody("Unauthorized: Missing or invalid JWT token");
            response.send();
            return;
        }
        next.next(request, response);
    }
}

class CustomMiddleware implements Middleware {
    @Override
    public void apply(Request request, Response response, MiddlewareChain next) throws IOException {
        System.err.println("Custom middleware logic here");
        // You can modify the request or response if needed
        next.next(request, response);
    }
}

class AppRouter {
    public Router create() {
        Router router = new Router();
        router.get("/hello", this::hello);
        router.post("/body", this::readBodyFromUrlEncodedForm);

        return router;
    }

    private void hello(Request req, Response resp) {
        resp.send("Hello from Cabin");
    }
    private void readBodyFromUrlEncodedForm(Request req, Response resp) {
        // Example of reading body from URL encoded form
        List<String> formFields = req.getFormFields();
        for(String field : formFields) {
            String formField = req.getFormField(field);
            System.err.println("Form field: " + field + " = " + formField);
        }
        resp.send("Hello from Cabin");
    }
}

public class CabinServerWithMiddleware {
    static CabinLogger.LoggerInstance _Logger = CabinLogger.getLogger(Router.class);


    public static void main(String[] args) throws IOException {
        CabinServer server;
        server = new ServerBuilder()
                .setPort(8888)
                .enableProfiler(true)
                .enableProfilerDashboard(true)
                .build();


        Router appRouter = new AppRouter().create();
        appRouter.use(new JwtMiddleware());
        appRouter.use(new CustomMiddleware());

        server.use("/api", appRouter);

        server.start(new ServerCallBack());
        _Logger.info("Starting Cabin server on port 8888");
    }
}
