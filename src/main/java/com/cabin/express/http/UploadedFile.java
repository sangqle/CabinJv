package com.cabin.express.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.time.Instant;

public class UploadedFile {
    private final String fileName;
    private final String contentType;
    private final byte[] content;
    private final Map<String, String> metadata;
    private Instant uploadTime;

    public UploadedFile(String fileName, String contentType, byte[] content) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
        this.metadata = new HashMap<>();
        this.uploadTime = Instant.now();
    }

    public UploadedFile(String fileName, String contentType, byte[] content, Map<String, String> metadata) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
        this.metadata = new HashMap<>(metadata);
        this.uploadTime = Instant.now();
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public int getSize() {
        return content.length;
    }

    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public String getMetadata(String key) {
        return metadata.get(key);
    }

    public String getMetadata(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    public Map<String, String> getAllMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public Instant getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(Instant uploadTime) {
        this.uploadTime = uploadTime;
    }
}