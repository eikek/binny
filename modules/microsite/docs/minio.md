---
layout: docs
position: 40
title: MinIO
permalink: minio
---

# MinIO

[MiniO](https://min.io/) is a S3 compatible object storage which can
be used to back a `BinaryStore`. It is available under a free license
and can be self hosted. This module provides an implementation that is
based on MinIOs [Java
SDK](https://docs.min.io/docs/java-client-quickstart-guide.html).

The SDK provides an easy to use asynchronous http client. It is
wrapped in an `Async`.

## Usage

To create such a store, you only need the endpoint url, the
credentials (an access- and secret key) and a mapping from a
`BinaryId` to a pair of values: `S3Key`. The MinIO store expects its
key to have a bucket and a filekey (or object id). Since a `BinaryId`
is just one value, the store must know how to create the two values,
bucket name and object id - which are combined in a `S3Key`.

The example below starts a container running MinIO in order to work.
The container knows the endpoint and keys and can create a config for
creating the store. It's only necessary to specify the mentioned
mapping. Here we use a constant bucket name _docs-bucket_.

```scala mdoc
import binny._
import binny.minio._
import binny.util.Logger
import ExampleData._
import fs2.Stream
import cats.effect.IO
import cats.effect.unsafe.implicits._

val logger = Logger.silent[IO]
val someData = ExampleData.file2M


val run =
  for {
    // start a MiniO container, which can create a valid config
    minio <- Stream.resource(DocUtil.startMinIOContainer)
    config: MinioConfig = minio.createConfig(S3KeyMapping.constant("docs-bucket"))

    // Create the `BinaryStore`
    store = MinioBinaryStore[IO](config, logger)

    // insert some data
    id <- someData.through(store.insert)

    // get the file out
    bin <- Stream.eval(
      store.findBinary(id, ByteRange(0, 100)).getOrElse(sys.error("not found"))
    )
    str <- Stream.eval(bin.readUtf8String)
  } yield str + "..."

run.compile.lastOrError.unsafeRunSync()
```

## MinioChunkedBinaryStore

The `MinioChunkedBinaryStore` implements `ChunkedBinaryStore` to allow
uploading files in independent chunks. This is useful if chunks are
received in random order and the whole file is not available as
complete stream.

For this the given `S3KeyMapping` is amended: the bucket is reused as
is, but the object-name is appended with `/chunk_00001` so the user
given objectname is turned into a folder and each chunk is stored
beneath. For binaries that are provided as a complete stream, it
stores just one chunk file - similar to what `MinioBinaryStore` does.

When a file is requested, all required chunks are loaded sequentially
and concatenated.
