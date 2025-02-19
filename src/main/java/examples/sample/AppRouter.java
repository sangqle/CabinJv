package examples.sample;

import com.cabin.express.http.UploadedFile;
import com.cabin.express.router.Router;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.List;

public class AppRouter {
    static Router router = new Router();

    static {
        setRouter();
    }

    protected static void setRouter() {
        router.setPrefix("/api/v1");
        router.get("/users/:userId", (req, res) -> {
            String userId = req.getPathParam("userId");
            JsonObject json = new JsonObject();
            json.addProperty("userId", userId);
            res.send(json);
        });
        router.post("/upload/:userId", (req, res) -> {
            Map<String, Object> body = req.getBody();
            List<UploadedFile> files = req.getUploadedFile("file");
            JsonObject json = new JsonObject();
            JsonArray nodes = new JsonArray();
            if (files == null) {
                json.addProperty("error", "No file uploaded");
                res.send(json);
                return;
            }
            for (UploadedFile file : files) {

                JsonObject node = new JsonObject();
                node.addProperty("fileName", file.getFileName());
                node.addProperty("contentType", file.getContentType());
                node.addProperty("size", file.getSize());
                nodes.add(node);
            }

            // convert the list to a JSON array
            json.addProperty("fields", req.getFormFields().toString());
            json.add("files", nodes);
            res.send(json);
        });
    }
}
