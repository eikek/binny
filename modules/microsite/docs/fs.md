---
layout: docs
position: 10
title: FS
permalink: fs
---

# File System

This wraps the [fs2 Files API](https://fs2.io/#/io?id=files) to create
a `BinaryStore`.

## Usage

You need a `FsStoreConfig` object to create an instance of the
`FsBinaryStore`. The companion object has some convenience
constructors.

The `FsBinaryStore.default` creates a store that saves files in a
subdirectory hierarchy.

```scala mdoc
import binny._
import binny.fs._
import fs2.io.file.{Files, Path}
import cats.effect.IO
import cats.effect.unsafe.implicits._
import fs2.Stream

val logger = binny.util.Logger.silent[IO]
val someData = ExampleData.file2M

// lets store two pieces and look at the outcome
val run =
  for {
    baseDir <- Stream.resource(DocUtil.tempDir)
    store = FsBinaryStore.default(logger, baseDir)
    id1 <- someData.through(store.insert)
    id2 <- someData.through(store.insert)
    layout <- Stream.eval(DocUtil.directoryContentAsString(baseDir))
  } yield (id1, id2, layout)

run.compile.lastOrError.unsafeRunSync()
```

This store uses the id to create a directory using the first two
characters and another below using the complete id. Then the data is
stored in `file` and its attributes in `attr`.

This can be changed by providing a different `FsStoreConfig`. The
mapping of an `id` to a file in the filesystem is given by a
`PathMapping`. There are some provided, the above results are from
`PathMapping.subdir2`.

As another example, the next `FsBinaryStore` puts the files directly
into the `baseDir` - using the id as its name.

```scala mdoc

val run2 =
  Stream.resource(DocUtil.tempDir).flatMap { baseDir =>
    val store = FsBinaryStore[IO](
      FsStoreConfig.default(baseDir).withMapping(PathMapping.simple),
      logger
    )
    someData.through(store.insertWith(BinaryId("hello-world.txt"))) ++
      someData.through(store.insertWith(BinaryId("hello_world.txt"))) ++
      Stream.eval(DocUtil.directoryContentAsString(baseDir))
  }

run2.compile.lastOrError.unsafeRunSync()
```

A `PathMapping` is a function `(Path, BinaryId) => Path)` where the
given path is the base directory. So you can easily create a custom
layout.


## FsChunkedBinaryStore

The `FsChunkedBinaryStore` implements `ChunkedBinaryStore` to allow
storing chunks independently. This is useful if chunks are received in
random order and the whole file is not available as complete stream.

This is implemented by storing each chunk as a file and concatenating
these when loading. Therefore, a `DirectoryMapping` is required that
maps a `BinaryId` to a directory (and not a file as `PathMapping`
does). For binaries that are provided as a complete stream, it stores
just one chunk file - same as `FsBinaryStore` does.

However, in order to use this the complete size of the file must be
known up front. This is needed to know when the last chunk is
received.
