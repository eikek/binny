---
layout: docs
position: 10
title: FS
permalink: fs
---

# FS module

This wraps the [fs2 Files API](https://fs2.io/#/io?id=files) to create
a `BinaryStore`.

## Usage

You need a `FsStoreConfig` object and a `BinaryAttributeStore` to
create an instance of the `FsBinaryStore`. The companion object has
some convenience constructors.

The `FsBinaryStore.default` creates a store that saves files in a
subdirectory hierarchy and stores the attributes in another file,
next to the file containing the data.

```scala mdoc
import binny._
import binny.ContentTypeDetect.Hint
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
    id1 <- someData.through(store.insert(Hint.none))
    id2 <- someData.through(store.insert(Hint.none))
    layout <- Stream.eval(DocUtil.directoryContentAsString(baseDir))
  } yield (id1, id2, layout)

run.compile.lastOrError.unsafeRunSync()
```

This store uses the id to create a directory using the first two
characters and another below using the complete id. Then the data is
stored in `file` and its attributes in `attr`. The store also uses the
`FsBinaryAttributeStore` to store the attributes in the file system.
This has been configured by the same strategy to have both files next
to each other.

This can be changed by providing a different `FsStoreConfig` and
`FsAttrConfig`, respectively. Of course, a different implementation of
`BinaryAttributeStore` can be used as well. The mapping of an `id` to
a file in the filesystem is given by a `PathMapping`. There are some
provided, the above results are from `PathMapping.subdir2`.

As another example, the next `FsBinaryStore` won't store any
attributes and puts the files directly into the `baseDir` - using the
id as its name.

```scala mdoc

val run2 =
  Stream.resource(DocUtil.tempDir).flatMap { baseDir =>
    val store = FsBinaryStore[IO](
      FsStoreConfig.default(baseDir).withMapping(PathMapping.simple),
      logger,
      BinaryAttributeStore.empty[IO]
    )
    someData.through(store.insertWith(BinaryId("hello-world.txt"), Hint.none)) ++
      someData.through(store.insertWith(BinaryId("hello_world.txt"), Hint.none)) ++
      Stream.eval(DocUtil.directoryContentAsString(baseDir))
  }

run2.compile.lastOrError.unsafeRunSync()
```

A `PathMapping` is a function `(Path, BinaryId) => Path)` where the
given path is the base directory. So you can easily create a custom
layout.
