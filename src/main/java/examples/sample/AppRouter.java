package examples.sample;

import com.cabin.express.http.UploadedFile;
import com.cabin.express.router.Router;
import com.google.gson.JsonObject;

import java.util.Map;

public class AppRouter {
    static Router router = new Router();

    static {
        setRouter();
    }

    protected static void setRouter() {
        router.setPrefix("/api/v1");
        router.post("/upload/:userId", (req, res) -> {
            Map<String, Object> body = req.getBody();
            UploadedFile file = req.getUploadedFile("file");
            JsonObject json = new JsonObject();
            if(file == null) {
                json.addProperty("error", "No file uploaded");
                res.send(json);
                return;
            }
            json.addProperty("fileName", file.getFileName());
            json.addProperty("contentType", file.getContentType());
            json.addProperty("fileSize", file.getSize());
            json.addProperty("userId", req.getPathParam("userId"));
            res.send(json);
        });
    }
}
