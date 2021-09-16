---
layout: docs
position: 30
title: PGLO
permalink: pglo
---

# PostgreSQL Large Objects

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

## Usage

For the examples to run, a PostgreSQL server is necessary. It is quite
easy to start one locally, for example with docker.

```scala mdoc
import binny._
import binny.util.Logger
import binny.Binary.Implicits._
import binny.jdbc.ConnectionConfig
import binny.pglo._
import fs2.Stream
import cats.effect.IO
import cats.effect.unsafe.implicits._


implicit val logger = Logger.silent[IO]

val someData = ExampleData.file2M

val run =
  for {
    // start a postgres container and create a DataSource that connects to it
    postgres <- Stream.resource(DocUtil.startPostgresContainer)
    ds = ConnectionConfig(postgres.jdbcUrl, postgres.username, postgres.password).dataSource

    // Create the `BinaryStore` and the schema
    store = PgLoBinaryStore.default[IO](logger, ds)
    _ <- Stream.eval(PgSetup.run[IO](store.config.table, logger, ds))

    // insert some data
    id <- someData.through(store.insert(Hint.none))

    // get the file out
    bin <- Stream.eval(
      store.findBinary(id, ByteRange(0, 100)).getOrElse(sys.error("not found"))
    )
    str <- bin.readUtf8String
  } yield str + "..."

run.compile.lastOrError.unsafeRunSync()
```

## JdbcBinaryStore

The `PgLoBinaryStore` also provides a `findBinaryStateful` variant
just like the `GenericJdbcStore`. The default `findBinary` method
creates a byte stream that loads the file in chunks. After every
chunk, the connection is closed again and the next chunk seeks into
the large object to start anew. In contrast, the `findBinaryStateful`
method uses a single connection for the entire stream.
