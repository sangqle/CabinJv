package com.cabin.express.http;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MultipartParser {
    private final Map<String, String> formFields = new HashMap<>();
    private final Map<String, UploadedFile> uploadedFiles = new HashMap<>();

    public MultipartParser(byte[] requestData, String contentType) throws Exception {
        parseMultipart(requestData, contentType);
    }

    private void parseMultipart(byte[] requestData, String contentType) throws Exception {
        String boundary = extractBoundary(contentType);
        if (boundary == null) {
            throw new IllegalArgumentException("Invalid multipart request: Missing boundary");
        }

        String requestString = new String(requestData, StandardCharsets.UTF_8);
        String[] parts = requestString.split("--" + boundary);

        for (String part : parts) {
            if (part.equals("--") || part.trim().isEmpty()) {
                continue; // Ignore boundary markers
            }
            parsePart(part.trim().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void parsePart(byte[] partBytes) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(partBytes), StandardCharsets.UTF_8));

        String contentDisposition = null;
        String contentType = null;
        String fieldName = null;
        String fileName = null;
        ByteArrayOutputStream fileContent = new ByteArrayOutputStream();

        // Read headers
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Disposition:")) {
                contentDisposition = line;
                fieldName = extractFieldName(contentDisposition);
                fileName = extractFileName(contentDisposition);
            } else if (line.startsWith("Content-Type:")) {
                contentType = line.split(": ")[1].trim();
            }
        }

        // Read body content
        while ((line = reader.readLine()) != null) {
            fileContent.write(line.getBytes(StandardCharsets.UTF_8));
            fileContent.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        byte[] contentBytes = fileContent.toByteArray();
        String contentString = new String(contentBytes, StandardCharsets.UTF_8).trim();

        // Determine if it's a file or form field
        if (fileName != null) {
            uploadedFiles.put(fieldName, new UploadedFile(fileName, contentType, contentBytes));
        } else if (fieldName != null) {
            formFields.put(fieldName, contentString);
        }
    }

    private String extractBoundary(String contentType) {
        String[] parts = contentType.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring("boundary=".length());
            }
        }
        return null;
    }

    private String extractFieldName(String contentDisposition) {
        Matcher matcher = Pattern.compile("name=\"([^\"]+)\"").matcher(contentDisposition);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractFileName(String contentDisposition) {
        Matcher matcher = Pattern.compile("filename=\"([^\"]+)\"").matcher(contentDisposition);
        return matcher.find() ? matcher.group(1) : null;
    }

    public Map<String, String> getFormFields() {
        return formFields;
    }

    public Map<String, UploadedFile> getUploadedFiles() {
        return uploadedFiles;
    }
}
