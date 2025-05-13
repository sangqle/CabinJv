# Release Notes

---

## [Unreleased]

### Added
- **Gzip Response Compression:**  
  Introduced Gzip middleware (`GzipMiddleware`) that automatically compresses HTTP responses if the client sends an `Accept-Encoding: gzip` header. This improves performance for clients that support it, especially for large payloads.

---

<!-- When finalizing a release, move the above to a section with the actual version and date -->