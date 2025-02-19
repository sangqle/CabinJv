package com.cabin.express.http;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class MultipartParser {
    private final Map<String, String> formFields = new HashMap<>();
    private final Map<String, List<UploadedFile>> uploadedFiles = new HashMap<>();
    private final String boundary;

    public MultipartParser(byte[] requestData, String contentType) throws Exception {
        this.boundary = extractBoundary(contentType);
        if (this.boundary == null) {
            throw new IllegalArgumentException("Invalid multipart request: Missing boundary");
        }
        parseMultipart(new ByteArrayInputStream(requestData));
    }

    private void parseMultipart(InputStream inputStream) throws Exception {
        // Wrap the stream so we can push back bytes if needed.
        PushbackInputStream pbis = new PushbackInputStream(inputStream, 4096);

        // Read the initial boundary line.
        String boundaryLine = readLine(pbis);
        if (boundaryLine == null || !boundaryLine.startsWith("--" + boundary)) {
            throw new IOException("Initial boundary not found");
        }

        while (true) {
            // Peek at the next line: if it is the final boundary, stop.
            String nextLine = readLine(pbis);
            if (nextLine == null) break;
            if (nextLine.equals("--" + boundary + "--")) {
                // Final boundary; end processing.
                break;
            }
            // Since the nextLine is not a boundary header (it belongs to the part),
            // push it back so that parsePart can read it as part of the headers.
            byte[] bytesToPushBack = (nextLine + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
            pbis.unread(bytesToPushBack);

            // Parse the current part.
            parsePart(pbis);

            // After processing a part, read the boundary line (which should be the separator)
            boundaryLine = readLine(pbis);
            if (boundaryLine == null || boundaryLine.equals("--" + boundary + "--")) {
                break;
            }
        }
    }

    private void parsePart(PushbackInputStream pbis) throws IOException {
        // --- 1. Read Part Headers ---
        String contentDisposition = null;
        String contentType = null;
        String fieldName = null;
        String fileName = null;

        String line;
        // Read header lines until an empty line is encountered
        while ((line = readLine(pbis)) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Disposition:")) {
                contentDisposition = line;
                fieldName = extractFieldName(contentDisposition);
                fileName = extractFileName(contentDisposition);
            } else if (line.startsWith("Content-Type:")) {
                contentType = line.substring("Content-Type:".length()).trim();
            }
        }

        // --- 2. Read the Body (Binary Data) Until the Next Boundary ---
        byte[] partData = readPartData(pbis, boundary);

        // --- 3. Store as File or Form Field ---
        if (fileName != null) {
            // File part: store in a list per field name
            List<UploadedFile> fileList = uploadedFiles.get(fieldName);
            if (fileList == null) {
                fileList = new ArrayList<>();
                uploadedFiles.put(fieldName, fileList);
            }
            fileList.add(new UploadedFile(fileName, contentType, partData));
        } else {
            // Regular form field: store its value as string
            formFields.put(fieldName, new String(partData, StandardCharsets.UTF_8).trim());
        }
    }

    /**
     * Reads a line from a PushbackInputStream. A line is terminated by CRLF.
     */
    private String readLine(PushbackInputStream pbis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int c = pbis.read();
        if (c == -1) {
            return null; // end of stream
        }
        while (c != -1) {
            if (c == '\r') {
                int next = pbis.read();
                if (next == '\n') {
                    break;
                } else {
                    pbis.unread(next);
                    break;
                }
            } else if (c == '\n') {
                break;
            } else {
                baos.write(c);
            }
            c = pbis.read();
        }
        return baos.toString(StandardCharsets.ISO_8859_1.name());
    }

    /**
     * Reads the binary data of the current part until the boundary marker is reached.
     * It scans the stream for the boundary pattern ("\r\n--" + boundary) and stops there.
     */
    private byte[] readPartData(PushbackInputStream pbis, String boundary) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Build the byte sequence that indicates the start of the boundary
        byte[] boundaryBytes = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        int boundaryLen = boundaryBytes.length;

        // Use a sliding window to detect the boundary.
        // We'll keep a buffer of last N bytes, where N == boundaryLen.
        byte[] window = new byte[boundaryLen];
        int windowPos = 0;
        boolean firstByte = true;

        int b;
        while ((b = pbis.read()) != -1) {
            // Write out previous buffered bytes if not matching boundary
            if (firstByte) {
                // For the very first byte of the part, we don't have a preceding CRLF.
                firstByte = false;
                window[windowPos++] = (byte) b;
                continue;
            }
            // Slide the window: add the new byte to the window.
            window[windowPos % boundaryLen] = (byte) b;
            windowPos++;

            // Write the byte to output (we might adjust later if we detect boundary)
            out.write(b);

            // Check if we have enough bytes in our sliding window to compare
            if (windowPos >= boundaryLen) {
                // Build the current window content in order
                byte[] currentWindow = new byte[boundaryLen];
                for (int i = 0; i < boundaryLen; i++) {
                    currentWindow[i] = window[(windowPos - boundaryLen + i) % boundaryLen];
                }
                // Compare with the boundary bytes
                if (Arrays.equals(currentWindow, boundaryBytes)) {
                    // Remove the boundary bytes from the output.
                    byte[] partData = out.toByteArray();
                    int dataLength = partData.length - boundaryLen;
                    // (if any exist, they will be processed by the main loop)
                    if (dataLength < 0) {
                        return new byte[0];
                    }
                    // Push back any extra bytes read after the boundary
                    return Arrays.copyOf(partData, dataLength);
                }
            }
        }
        return out.toByteArray();
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

    public Map<String, List<UploadedFile>> getUploadedFiles() {
        return uploadedFiles;
    }
}
