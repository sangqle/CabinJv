package com.cabin.express.http;

/**
 * Represents an uploaded file.
 * @author Sang Le
 * @version 1.0.0
 * @since 2024-12-24
 * <p>
 *     This class is used to store information about an uploaded file.
 *     It contains the file name, content type, and the file data.
 *     The file data is stored as a byte array.
 *     This class is used in the {@link Request} class to handle file uploads.
 *     The file data can be saved to a file on the server or processed in memory.
 *     The file data can be accessed using the {@link #getFileData()} method.
 *     The file name and content type can be accessed using the {@link #getFileName()} and {@link #getContentType()} methods.
 * </p>
 */
public class UploadedFile {
    private final String fileName;
    private final String contentType;
    private final byte[] fileData;

    public UploadedFile(String fileName, String contentType, byte[] fileData) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileData = fileData;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getFileData() {
        return fileData;
    }
}
