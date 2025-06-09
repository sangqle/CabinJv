---
id: response
title: Response
---

# The Response Object

In CabinJ, the `Response` object represents the outgoing HTTP response that will be sent to the client. It provides a comprehensive API for setting the HTTP status code, managing headers, setting cookies, writing the response body in various formats (text, JSON, binary), enabling GZIP compression, and finally sending the response over the network.

## Core Features

A `Response` object allows you to:

*   **Set Status Code**: Define the HTTP status of the response (e.g., 200 OK, 404 Not Found).
*   **Manage Headers**: Add, set, or retrieve HTTP response headers.
*   **Manage Cookies**: Set new cookies with various attributes or clear existing ones.
*   **Write Response Body**: Populate the body with plain text, JSON objects (from Maps or any POJO), or raw binary data.
*   **Enable Compression**: Optionally compress the response body using GZIP for reduced transfer size.
*   **Send Response**: Finalize and transmit the HTTP response to the client.

## Setting Status Code

You can set the HTTP status code for the response.

```java
// Inside a handler or middleware
res.setStatusCode(201); // 201 Created
int currentStatus = res.getStatusCode(); // Retrieves 201
```
The corresponding status message (e.g., "Created") is automatically determined using `HttpStatusCode.getStatusMessage(statusCode)`.

## Managing Headers

Headers are stored in a case-insensitive manner.

### Setting a Header

If the header already exists, its value is updated.

```java
res.setHeader("Content-Type", "application/json; charset=utf-8");
res.setHeader("X-Request-ID", "some-unique-id");
```

### Adding a Header

This is equivalent to `setHeader` as the underlying map will overwrite if the key exists.

```java
res.addHeader("Cache-Control", "no-cache");
```

### Getting a Header

```java
String contentType = res.getHeader("Content-Type");
```

### Getting All Headers

```java
Map<String, String> allHeaders = res.getHeaders();
allHeaders.forEach((key, value) -> System.out.println(key + ": " + value));
```

### Setting Content Type

A dedicated method for setting the `Content-Type` header.

```java
res.setContentType("text/html; charset=UTF-8");
```
Throws `IllegalArgumentException` if `contentType` is null or empty.

## Managing Cookies

CabinJ provides flexible methods to set and clear cookies.

### Setting a Cookie

Multiple overloads allow for setting cookies with varying levels of detail.

```java
// Basic cookie
res.setCookie("myCookie", "cookieValue");

// With domain
res.setCookie("sessionToken", "abc123xyz", "example.com");

// With domain and path
res.setCookie("userPref", "darkMode", "myapp.com", "/settings");

// With domain, path, and expires (RFC 1123 date string)
res.setCookie("promoCode", "SUMMER25", "store.example.com", "/", "Wed, 09 Jun 2027 10:18:14 GMT");

// With all attributes: domain, path, expires, httpOnly, secure
res.setCookie("authToken", "secureTokenValue", ".app.com", "/", "Fri, 09 Jun 2028 10:00:00 GMT", true, true);
```
Default values if not specified:
*   Domain: `""` (current host)
*   Path: `/`
*   Expires: `""` (session cookie)
*   HttpOnly: `false`
*   Secure: `false`

### Clearing a Cookie

To clear a cookie, you set its expiration date to a time in the past.

```java
res.clearCookie("myCookie", "example.com", "/"); // Clears 'myCookie' for the specified domain and path
```
This effectively sets the cookie's value to empty and its `Expires` attribute to "Thu, 01 Jan 1970 00:00:00 GMT".

## Writing the Response Body

The response body can be text, JSON, or binary data.

### 1. Plain Text

Appends the given string to the internal response body buffer.

```java
res.writeBody("Hello, CabinJ User!");
res.writeBody(" Welcome to our platform."); // Appends further
```

### 2. JSON Data

Serializes any given Java object (including Maps, Lists, or custom POJOs) to a JSON string and appends it to the body. This method also automatically sets the `Content-Type` header to `application/json`.

```java
// Example with a Map
Map<String, Object> jsonDataMap = Map.of("id", 123, "name", "CabinJ Item");
res.writeBody(jsonDataMap); // Sets Content-Type and writes JSON string

// Example with a custom POJO
// public class UserDto {
//     private String username;
//     private String email;
//     // Constructors, getters, setters
// }
UserDto user = new UserDto("cabinUser", "cabin@example.com");
res.writeBody(user); // Serializes UserDto to JSON and sets Content-Type

// If content is null, an empty string is appended for the body.
// res.writeBody(null);
```

### 3. Binary Data

Writes raw bytes to an internal `ByteArrayOutputStream`. This is useful for serving files or other binary content. If there's existing text content in the `body` StringBuilder, it's converted to bytes and written to the stream first.

```java
// Assuming imageBytes is a byte[]
// byte[] imageBytes = Files.readAllBytes(Paths.get("path/to/image.png"));
res.write(imageBytes, 0, imageBytes.length);
```

## Enabling Compression

You can enable GZIP compression for the response body.

```java
res.enableCompression();
boolean isEnabled = res.isCompressionEnabled(); // true
```
If enabled, the response body will be GZIP-compressed before sending, and the `Content-Encoding: gzip` header will be automatically added.

## Sending the Response

The `send()` methods finalize the response (assemble headers, compress body if enabled, calculate Content-Length) and write it to the client's `SocketChannel`.

### Sending the Accumulated Response

Sends the currently configured status, headers, cookies, and the accumulated body content.

```java
res.setStatusCode(200);
res.setHeader("X-App-Version", "1.2.3");
res.writeBody("Final content.");
res.send();
```

### Sending Content Directly

Convenience methods to write content and send the response in one step. The `send(Object content)` method intelligently handles the content type:
*   If `content` is a `String`, it's sent as plain text.
*   If `content` is a `byte[]`, it's sent as binary data.
*   For any other `Object` type (e.g., a Map, List, or custom POJO), it's serialized to JSON, and the `Content-Type` is set to `application/json`.

```java
// Send plain text
res.send("This is a complete plain text response.");

// Send a JSON object (from a Map)
Map<String, String> statusMessage = Map.of("status", "success", "message", "Operation completed.");
res.send(statusMessage); // Automatically sets Content-Type to application/json

// Send a JSON object (from a custom POJO)
// public class Product { private String name; private double price; /* ... */ }
Product product = new Product("Laptop", 999.99);
res.send(product); // Serializes Product to JSON, sets Content-Type

// Send binary data
// byte[] pdfBytes = Files.readAllBytes(Paths.get("document.pdf"));
// res.setContentType("application/pdf"); // Set content type for binary data if needed
// res.send(pdfBytes);
```

## Important Considerations & Internal Behavior

*   **SocketChannel**: The `Response` object holds a reference to the client's `SocketChannel` to write data directly.
*   **Buffering**: Text content is initially accumulated in a `StringBuilder`. Binary content (or text converted to binary) is written to a `ByteArrayOutputStream`.
*   **Finalization in `send()`**:
    1.  The HTTP status line is constructed.
    2.  The final response body bytes are determined (from `ByteArrayOutputStream` or `StringBuilder`).
    3.  If compression is enabled, the body bytes are GZIP-compressed.
    4.  The `Content-Length` header is calculated based on the (potentially compressed) body size.
    5.  All headers (including `Content-Encoding` if compressed, `Content-Length`, and `Set-Cookie`) are assembled.
    6.  Headers and body are written to the `SocketChannel` as `ByteBuffer`s.
*   **Error Handling**: `send()` includes basic error handling for `IOException`s like "Broken pipe" or "Connection reset", logging other errors.
*   **State after `send()`**: The internal `ByteArrayOutputStream` is reset after `send()` is called.
*   **`isSend()`**: This method indicates if content has been written to the internal `bufferOut` (binary buffer). It does not reflect content solely in the text `body` StringBuilder.
*   **`toByteBuffer()`**: Returns a `ByteBuffer` wrapping the content of the internal `bufferOut`. If `bufferOut` is null or empty, an empty `ByteBuffer` is returned. It does not include content from the text `body` StringBuilder unless `write(byte[], ...)` was called after text was added to `body`.

## Example Usage

```java
// In a CabinServer handler
server.get("/greet", (req, res) -> {
    res.setStatusCode(200);
    res.setContentType("text/plain; charset=UTF-8");
    res.setCookie("greeting", "hello", "example.com", "/", "Wed, 09 Jun 2027 10:00:00 GMT", true, false);
    res.writeBody("Hello from CabinJ!");
    res.send();
});

// Example with POJO response
// public class ApiResponse {
//     private String status;
//     private Object data;
//     // Constructors, getters, setters
// }
server.post("/submit-data", (req, res) -> {
    // ... process request ...
    Object processedData = req.getBody(); // Assuming req.getBody() gives parsed JSON or other object
    ApiResponse apiResponse = new ApiResponse("success", processedData);

    res.enableCompression(); // Enable GZIP for this potentially larger JSON response
    res.send(apiResponse); // Sends ApiResponse POJO as JSON with GZIP encoding
});

server.get("/download/report.pdf", (req, res) -> {
    try {
        byte[] pdfData = Files.readAllBytes(Paths.get("reports/report.pdf"));
        res.setContentType("application/pdf");
        res.setHeader("Content-Disposition", "attachment; filename=\"report.pdf\"");
        res.send(pdfData);
    } catch (IOException e) {
        res.setStatusCode(500);
        res.send("Error reading file."); // Sends plain text error
    }
});
```

The `Response` object in CabinJ provides a robust and developer-friendly way to construct and deliver HTTP responses.