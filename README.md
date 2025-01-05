# CabinJ Framework

CabinJ is a simple and lightweight HTTP server framework using Java NIO. It allows you to create and manage routes, apply middleware, and handle concurrent requests efficiently.

## Release Notes

### Version 1.0.0

- **Routing**
    - Added support for defining routes with `GET`, `POST`, `PUT`, and `DELETE` methods.
    - Support for path parameters and query parameters.

- **Middleware**
    - Added middleware support for processing requests and responses.
    - Global middleware can be applied to all routes.

- **CORS**
    - Implemented full-featured CORS middleware.
    - Configurable allowed origins, methods, headers, and credentials.

- **HTTP Status Codes**
    - Added `HttpStatusCode` class with common HTTP status codes and messages.

- **Cookie Management**
    - Enhanced cookie feature to set domain, path, expires, HttpOnly, and secure attributes.
    - Added methods to clear cookies by setting expiration to a past date.

- **Logging**
    - Integrated `CabinLogger` for logging server activities and errors.

- **Server Configuration**
    - Added `ServerBuilder` for easy server configuration and initialization.
    - Configurable port and thread pool size.

- **Concurrent Request Handling**
    - Efficient handling of concurrent requests using Java NIO and a worker pool.

- **JSON Support**
    - Added support for reading and writing JSON bodies in requests and responses.

- **Error Handling**
    - Improved error handling for route processing and middleware execution.

These release notes summarize the key features and improvements in the current version of your framework.


## Features

## Getting Started

### Prerequisites

- Java 11 or higher
- Gradle

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

## License

This project is licensed under the MIT License.