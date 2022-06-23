---
layout: docs
position: 10
title: Core API
permalink: core
---

# Core API

The core module depends on the [fs2](https://fs2.io/) library to
provide a convenient api.

The main idea is to have a uniform api for storing/retrieving files
across a variety of backends. The most focus is on sql databases. The
[jdbc](../jdbc) and [pglo](../pglo) module aim to provide an efficient
way to store even large files in databases. But there is the
[minio](../minio) module which provides a S3 compatible object storage
backend.

A file here is a `Binary[F]` which is a type alias for `Stream[F,
Byte]`. The `BinaryAttributes` holds some attributes for a binary, the
length, content type and sha256 hash.

The `BinaryStore[F]` is the main entry point and defines
storing/retrieving `Binary[F]`. `BinaryAttributes` can be computed for
any `Binary[F]`, but a `BinaryStore` can provide a more efficient way.


## Utilities

### Logging

Binny defines its own logger interface, to not impose a concrete one
on you. You can implement `Logger[F]` based on some real logging
library, like for example [log4s](https://github.com/Log4s/log4s); or
just use the `Logger.silent` option for no logging and `Logger.stdout`
option for logging to stdout.

```scala mdoc
import binny.util.Logger
import cats.effect.IO

val loggingOff = Logger.silent[IO]
val stdout = Logger.stdout[IO]()
```
