package com.cabin.express.http;

import com.cabin.express.loggger.CabinLogger;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/**
 * Represents an HTTP request.
 *
 * @author Sang Le
 * @version 1.0.0
 * @since 2025-02-23
 */
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

    /**
     * Parses the multipart data from the input stream.
     *
     * @param inputStream The input stream containing the multipart data.
     * @throws Exception If an error occurs during parsing.
     */
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
        Map<String, String> headers = new HashMap<>();
        Map<String, String> metadata = new HashMap<>();
        String contentDisposition = null;
        String contentType = null;
        String fieldName = null;
        String fileName = null;

        String line;
        // Read header lines until an empty line is encountered
        while ((line = readLine(pbis)) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(":");
            if (colonIndex > 0) {
                String headerName = line.substring(0, colonIndex).trim();
                String headerValue = line.substring(colonIndex + 1).trim();
                headers.put(headerName, headerValue);

                // Store all headers as metadata
                metadata.put("header." + headerName, headerValue);

                if (headerName.equalsIgnoreCase("Content-Disposition")) {
                    contentDisposition = headerValue;
                    fieldName = extractFieldName(contentDisposition);
                    fileName = extractFileName(contentDisposition);

                    // Extract and store all parameters from Content-Disposition
                    extractContentDispositionParams(contentDisposition, metadata);
                } else if (headerName.equalsIgnoreCase("Content-Type")) {
                    contentType = headerValue;
                }
            }
        }

        byte[] partData = readPartData(pbis, boundary);

        if (fileName != null) {
            // Add more metadata
            metadata.put("size", String.valueOf(partData.length));
            metadata.put("fieldName", fieldName);

            // File part: store in a list per field name
            List<UploadedFile> fileList = uploadedFiles.computeIfAbsent(fieldName, k -> new ArrayList<>());
            fileList.add(new UploadedFile(fileName, contentType, partData, metadata));
        } else {
            // Regular form field: store its value as string
            formFields.put(fieldName, new String(partData, StandardCharsets.UTF_8).trim());
        }
    }

    /**
     * Extracts parameters from Content-Disposition header and adds them to metadata
     */
    private void extractContentDispositionParams(String contentDisposition, Map<String, String> metadata) {
        if (contentDisposition == null) return;

        String[] parts = contentDisposition.split(";");
        for (String part : parts) {
            part = part.trim();
            int equalsIndex = part.indexOf('=');
            if (equalsIndex > 0) {
                String name = part.substring(0, equalsIndex).trim();
                String value = part.substring(equalsIndex + 1).trim();

                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }

                metadata.put("disposition." + name, value);
            } else {
                // Handle the disposition type (e.g., "form-data")
                metadata.put("disposition.type", part);
            }
        }
    }

    /**
     * Reads a line from a PushbackInputStream. A line is terminated by CRLF.
     */
    private String readLine(PushbackInputStream pbis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
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
            return baos.toString(StandardCharsets.ISO_8859_1);
        } catch (Throwable ex) {
            CabinLogger.error(String.format("Error reading line: %s", ex.getMessage()), ex);
        }
        return null;
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
            // write the byte to the output stream
            out.write(b);

            // Update the sliding windoe
            if (windowPos < boundaryLen) {
                window[windowPos++] = (byte) b;
            } else {
                // Shift the window to the left
                System.arraycopy(window, 1, window, 0, boundaryLen - 1);
                window[boundaryLen - 1] = (byte) b;
            }

            // Check if the boundary is reached
            if (windowPos == boundaryLen && Arrays.equals(window, boundaryBytes)) {
                // Remove the boundary from the output stream
                byte[] partData = out.toByteArray();
                int dataLength = partData.length - boundaryLen;
                return Arrays.copyOf(partData, dataLength);
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
        if (contentDisposition == null || contentDisposition.isEmpty()) {
            return null;
        }
        // 1. Try extracting filename*=UTF-8''encoded-filename
        Pattern patternFilenameStar = Pattern.compile("filename\\*=([^;]+)");
        Matcher matcher = patternFilenameStar.matcher(contentDisposition);
        if (matcher.find()) {
            String filenameStar = matcher.group(1).trim();
            int idx = filenameStar.indexOf("''"); // Locate charset separator
            if (idx != -1) {
                String charset = filenameStar.substring(0, idx);
                String encodedFilename = filenameStar.substring(idx + 2);
                try {
                    String decodedFilename = URLDecoder.decode(encodedFilename, charset);
                    return sanitizeFileName(decodedFilename);
                } catch (Exception e) {
                    CabinLogger.error(String.format("Error decoding filename: %s", e.getMessage()), e);
                }
            }
        }

        // 2. Try extracting filename="..."
        Pattern patternFilename = Pattern.compile("filename=\"([^\"]+)\"");
        matcher = patternFilename.matcher(contentDisposition);
        if (matcher.find()) {
            String filename = matcher.group(1);

            // Fix potential encoding issues from ISO-8859-1
            filename = new String(filename.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);

            return sanitizeFileName(filename);
        }

        return null;
    }

    // Sanitize file name: Fix encoding, remove unwanted characters
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return null;

        // Normalize Unicode (fixes smart apostrophes)
        fileName = Normalizer.normalize(fileName, Normalizer.Form.NFKD);

        // Replace smart apostrophes with standard ones
        fileName = fileName.replace("’", "'");

        // Remove control characters (non-printable) but keep punctuation.
        fileName = fileName.replaceAll("\\p{Cntrl}", "");

        // Optionally, remove characters that are not allowed in file systems (e.g., on Windows)
        // fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "");

        return fileName;
    }

    public Map<String, String> getFormFields() {
        return formFields;
    }

    public Map<String, List<UploadedFile>> getUploadedFiles() {
        return uploadedFiles;
    }
}
