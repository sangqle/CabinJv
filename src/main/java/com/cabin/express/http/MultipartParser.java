package com.cabin.express.http;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class MultipartParser {
    private final Map<String, String> formFields = new HashMap<>();
    private final Map<String, UploadedFile> uploadedFiles = new HashMap<>();
    private final String boundary;

    public MultipartParser(byte[] requestData, String contentType) throws Exception {
        this.boundary = extractBoundary(contentType);
        if (this.boundary == null) {
            throw new IllegalArgumentException("Invalid multipart request: Missing boundary");
        }
        parseMultipart(new ByteArrayInputStream(requestData));
    }

    private void parseMultipart(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.ISO_8859_1));
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("--" + boundary)) {
                parsePart(reader, inputStream);
            }
        }
    }

    private void parsePart(BufferedReader reader, InputStream inputStream) throws IOException {
        String contentDisposition = null;
        String contentType = null;
        String fieldName = null;
        String fileName = null;

        ByteArrayOutputStream fileContent = new ByteArrayOutputStream();
        boolean isFile = false;

        // **1. Read Headers**
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Disposition:")) {
                contentDisposition = line;
                fieldName = extractFieldName(contentDisposition);
                fileName = extractFileName(contentDisposition);
                if (fileName != null) isFile = true;
            } else if (line.startsWith("Content-Type:")) {
                contentType = line.split(": ")[1].trim();
            }
        }

        // **2. Read the Body as Raw Binary Data**
        InputStream rawInput = new BufferedInputStream(inputStream);
        byte[] buffer = new byte[4096]; // Use a buffer to efficiently read large files
        int bytesRead;
        while ((bytesRead = rawInput.read(buffer)) != -1) {
            if (isBoundary(buffer, bytesRead)) {
                break; // Stop reading at the next boundary
            }
            fileContent.write(buffer, 0, bytesRead);
        }

        byte[] contentBytes = fileContent.toByteArray();

        // **3. Store Form Fields or Files**
        if (isFile) {
            uploadedFiles.put(fieldName, new UploadedFile(fileName, contentType, contentBytes));
        } else {
            formFields.put(fieldName, new String(contentBytes, StandardCharsets.UTF_8).trim());
        }
    }

    private boolean isBoundary(byte[] buffer, int bytesRead) {
        String data = new String(buffer, 0, bytesRead, StandardCharsets.ISO_8859_1);
        return data.startsWith("--" + boundary);
    }

    private String extractBoundary(String contentType) {
        Matcher matcher = Pattern.compile("boundary=([^;]+)").matcher(contentType);
        return matcher.find() ? matcher.group(1) : null;
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
