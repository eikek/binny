---
layout: docs
position: 30
title: PGLO
permalink: pglo
---

# PGLO module

This module utilises PostgreSQLs [Large
Objects](https://www.postgresql.org/docs/current/largeobjects.html) to
implement a `BinaryStore`. This is then only for PostgreSQL, and it
also depends on the postgresql jdbc driver.

Using large objects, postgresql stores the data outside its standard
table space and the object can be referenced by an id. They also allow
to seek into a specific position, which is used to implement loading
partial data.

The `PgLoBinaryStore` also implements `JdbcBinaryStore` and provides
two methods to retrieve a binary. One, the default `findBinary`, uses
one connection per chunk. The other, `findBinaryStateful` uses a
connection for the entire stream.

For the examples to run, a PostgreSQL server is necessary. It is quite
easy to start one locally, for example with docker.

```scala mdoc
import binny._
import binny.ContentTypeDetect.Hint
import binny.util.Logger
import binny.Binary.Implicits._
import binny.jdbc._
import cats.effect.IO
import cats.effect.unsafe.implicits._

val dataSource = ConnectionConfig.h2Memory("docs2").dataSource
implicit val logger = Logger.silent[IO]
val store = GenericJdbcStore.default[IO](dataSource, Logger.silent[IO])

// creates the schema, the filenames are from the default config
DatabaseSetup.runBoth[IO](Dbms.H2, dataSource, "file_chunk", "file_attr").unsafeRunSync()

val someData = ExampleData.file2M
val id = someData.through(store.insert(Hint.none)).compile.lastOrError.unsafeRunSync()

// get the file out
store.findBinary(id, ByteRange.All).getOrElse(sys.error("not found"))
  .flatMap(binary => binary.readUtf8String.compile.lastOrError)
  .unsafeRunSync()
```
