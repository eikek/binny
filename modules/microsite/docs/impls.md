---
layout: docs
title: Implementation Modules
permalink: impls
---

# Implementation Modules

These modules provide implementations for `BinaryStore` and some also
for `BinaryAttributeStore`.

- [file system](fs) wrapping the [fs2 Files API](https://fs2.io/#/io?id=files)
- [JDBC](jdbc) provides a generic implementation using pure JDBC
- [PostgreSQL Large Object](pglo) using PostgreSQLs [Large Objects](https://www.postgresql.org/docs/current/largeobjects.html)
- [MinIO](minio) provides a S3 compatible store based on the SDK from [MinIO](https://docs.min.io/docs/java-client-quickstart-guide.html)
