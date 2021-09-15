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

val logger = binny.util.Logger.silent[IO]
val baseDir = Path("./binny-fs-test")
val store = FsBinaryStore.default(logger, baseDir)
val someData = ExampleData.helloWorld[IO]

// lets store two pieces and look at the outcome
val run =
  for {
    _ <- fs2.Stream.eval(Files[IO].deleteRecursively(baseDir))
    id1 <- someData.through(store.insert(Hint.none))
    id2 <- someData.through(store.insert(Hint.none))
  } yield (id1, id2)

val ids = run.compile.lastOrError.unsafeRunSync()

DocUtil.directoryContentAsString(baseDir).unsafeRunSync()
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
val store2 = FsBinaryStore[IO](
  FsStoreConfig.default(baseDir).withMapping(PathMapping.simple),
  logger,
  BinaryAttributeStore.empty[IO]
)

val run2 =
  fs2.Stream.eval(Files[IO].deleteRecursively(baseDir)) ++
    someData.through(store2.insertWith(BinaryId("hello-world.txt"), Hint.none)) ++
    someData.through(store2.insertWith(BinaryId("hello_world.txt"), Hint.none))

run2.compile.drain.unsafeRunSync()
DocUtil.directoryContentAsString(baseDir).unsafeRunSync()
```

A `PathMapping` is a function `(Path, BinaryId) => Path)` where the
given path is the base directory. So you can easily create a custom
layout.
