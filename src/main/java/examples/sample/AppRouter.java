package examples.sample;

import com.cabin.express.http.UploadedFile;
import com.cabin.express.router.Router;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
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
                byte[] nameBytes = file.getFileName().getBytes(StandardCharsets.UTF_8);
                System.out.println("Encoded File Name (Hex): " + bytesToHex(nameBytes));
                JsonObject node = new JsonObject();
                node.addProperty("fileName", file.getFileName());
                node.addProperty("contentType", file.getContentType());
                node.addProperty("size", file.getSize());
                nodes.add(node);
            }

            // write the files to the disk
            writeFiles(files);

            // convert the list to a JSON array
            json.addProperty("fields", req.getFormFields().toString());
            json.add("files", nodes);
            res.send(json);
        });
    }

    public static void writeFiles(List<UploadedFile> files) {
        for (UploadedFile file : files) {
            try {
                // Create dir if not exists
                File dir = new File("uploads");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File newFile = new File("uploads/" + file.getFileName());
                java.nio.file.Files.write(newFile.toPath(), file.getContent());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X ", b));
        }
        return hexString.toString();
    }
}
