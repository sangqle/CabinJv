---
id: request
title: Request
---

# The Request Object

In CabinJ, the `Request` object represents an incoming HTTP request. It provides a comprehensive API to access various parts of the request, such as headers, path, query parameters, path parameters, and the request body in different formats. The server automatically parses the incoming raw HTTP request and populates this object for you.

## Core Components

A `Request` object encapsulates:

*   **HTTP Method**: e.g., GET, POST, PUT.
*   **Path**: The requested URL path (e.g., `/users/123`).
*   **Headers**: A map of HTTP request headers.
*   **Query Parameters**: Parameters from the URL's query string (e.g., `?name=cabin&version=1`).
*   **Path Parameters**: Parameters extracted from the route path (e.g., `:id` in `/users/:id`). These are typically populated by the [Router](./routing.md).
*   **Request Body**: The payload of the request, accessible as a raw string, a JSON map, or a deserialized Java object.
*   **Form Fields**: Data submitted via `application/x-www-form-urlencoded` or `multipart/form-data`.
*   **Uploaded Files**: Files submitted via `multipart/form-data`.
*   **Client IP Address**: The IP address of the client making the request, with support for proxy headers.
*   **Custom Attributes**: A mechanism to store request-scoped data.

**Thread Safety**: Instances of the `Request` class are not thread-safe. They are designed to be used by a single thread handling an individual HTTP request.

## Accessing Request Information

### Method and Path

```java
// Inside a handler or middleware
String method = req.getMethod(); // "GET", "POST", etc.
String path = req.getPath();     // e.g., "/users/profile"
String baseUrl = req.getBaseUrl(); // e.g., "http://localhost:8080" (if set by server)
```

### Headers

Headers are case-insensitive.

```java
String contentType = req.getHeader("Content-Type");
String userAgent = req.getHeader("User-Agent");

Map<String, String> allHeaders = req.getHeaders();
allHeaders.forEach((key, value) -> {
    System.out.println(key + ": " + value);
});
```

### Query Parameters

Query parameters are extracted from the URL string after the `?`.

```java
// For a request to /search?q=cabin&page=1

String searchTerm = req.getQueryParam("q"); // "cabin"
Integer pageNumber = req.getQueryParamAsInt("page", 1); // 1 (with default)
Long limit = req.getQueryParamAsLong("limit", 10L); // 10 (with default)

Map<String, String> allQueryParams = req.getQueryParams();
```

### Path Parameters

Path parameters are defined in your routes (e.g., `/users/:userId`) and populated by the Router.

```java
// For a route like router.get("/users/:userId/orders/:orderId", ...)
// and a request to /users/123/orders/456

String userId = req.getPathParam("userId"); // "123"
Integer orderId = req.getPathParamAsInt("orderId", null); // 456
Long numericUserId = req.getPathParamAsLong("userId", null); // 123L
```

## Working with the Request Body

CabinJ automatically attempts to parse the request body based on the `Content-Type` header.

### Raw Body as String

```java
String rawBody = req.getBodyAsString();
System.out.println("Raw request body: " + rawBody);
```

### JSON Body

If the `Content-Type` is `application/json`, the body is automatically parsed.

**1. As a `Map<String, Object>`:**

```java
// For a JSON body like: {"name": "CabinJ", "version": 1.0}
Map<String, Object> jsonBody = req.getBody(); // This is the default for getBody() with JSON
if (jsonBody != null) {
    String name = (String) jsonBody.get("name");
    Double version = (Double) jsonBody.get("version"); // Numbers are often Doubles
}
```

**2. As a Custom Java Object (POJO):**

You need a corresponding Java class (e.g., `MyPayload`) and Gson (which CabinJ uses internally) must be able to deserialize into it.

```java
// Assuming a class:
// public class MyPayload {
//     private String name;
//     private double version;
//     // getters and setters
// }

MyPayload payload = req.getBodyAs(MyPayload.class);
if (payload != null) {
    System.out.println("Payload Name: " + payload.getName());
}

// For generic types (e.g., List<MyObject>)
// import java.lang.reflect.Type;
// import com.google.gson.reflect.TypeToken;
// Type listType = new TypeToken<List<MyObject>>() {}.getType();
// List<MyObject> objects = req.getBodyAs(listType);
```

### Form Data (`application/x-www-form-urlencoded` or `multipart/form-data`)

**1. Form Fields:**

```java
String username = req.getFormField("username");
String password = req.getFormField("password", "defaultPassword"); // With default

List<String> allFieldNames = req.getFormFields(); // Get names of all submitted fields
```

**2. Uploaded Files (`multipart/form-data`):**

Files are accessible as `UploadedFile` objects, which contain the filename, content type, and file data as bytes. A single field name can have multiple files.

```java
// Assuming a form field named "profileImage" used for file upload
List<UploadedFile> files = req.getUploadedFile("profileImage");
if (files != null && !files.isEmpty()) {
    UploadedFile profilePic = files.get(0); // Get the first file if multiple
    String fileName = profilePic.getFilename();
    String contentType = profilePic.getContentType();
    byte[] fileData = profilePic.getData();

    // Process the fileData (e.g., save to disk)
    // Files.write(Paths.get("./uploads/" + fileName), fileData);
}
```
The `UploadedFile` class typically has methods like:
*   `getFilename()`: Original filename from the client.
*   `getContentType()`: MIME type of the file.
*   `getData()`: File content as `byte[]`.
*   `getSize()`: Size of the file in bytes.

## Client IP Address

CabinJ attempts to determine the client's true IP address, even when behind proxies, by checking common headers like `X-Forwarded-For`.

```java
String clientIp = req.getIpAddress();
```
The server should call `req.setRemoteIpAddress(socket.getRemoteSocketAddress().getAddress().getHostAddress())` when the connection is first established to provide the direct connection IP as a fallback. The `getIpAddress()` method then uses a list of standard headers to find a more accurate client IP if proxies are involved.

## Custom Attributes

You can store and retrieve request-scoped data using attributes. This is useful for passing data between middleware or from middleware to handlers. Attributes are typed.

```java
// In a middleware:
User authenticatedUser = userService.authenticate(req);
if (authenticatedUser != null) {
    req.putAttribute(User.class, authenticatedUser);
}

// In a later middleware or handler:
User currentUser = req.getAttribute(User.class);
if (currentUser != null) {
    System.out.println("Current user: " + currentUser.getUsername());
}
```

## Internal Parsing

The `Request` object is typically constructed by the server from a `ByteArrayOutputStream` or `byte[]` containing the raw HTTP request data. It then parses:
1.  The request line (method, full path, HTTP version).
2.  Headers.
3.  The path and query string from the full path.
4.  The body, based on `Content-Length` and `Content-Type` (URL-encoded, multipart, JSON).

This parsing happens automatically before your handlers or middleware receive the `Request` object.