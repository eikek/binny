---
layout: docs
position: 20
title: JDBC
permalink: jdbc
---

# JDBC module

This module provides a `BinaryStore` and `BinaryAttributeStore` using
JDBC. While it is tested for only H2, PostgreSQL and MariaDB, it
probably supports other database systems as well. The module doesn't
include any JDBC driver, you need to pull in the one you want to use
in your project.

The approach is as follows: the byte stream is split in chunks (size
can be configured) and each chunk is stored in a blob column and
associated to the same binary id. Since the blob datatype
characteristics differ between databases, you should define the table
yourself or use the provided setup for PostgreSQL or MariaDB. See the
`CreateDataTable` for the table definitions.

This makes range requests efficient, since it is possible to calculate
at what chunk to start (and to end if applicable). Also, the blob
datatype is sometimes restricted (or not efficient for large data) and
can have an upper limit this way.

When streaming, each chunk is loaded in memory at a time. The chunk
size defines the amount of memory used to stream a file (no matter its
size).


## Usage

You need to provide a `javax.sql.DataSource`. How to create this is
out of scope for this project. A `JdbcStoreConfig` is required, that
defines some settings, like the table name and chunk size to use.

For the examples here, an in-memory database
([H2](https://h2database.com) is used.

```scala mdoc
import binny._
import binny.ContentTypeDetect.Hint
import binny.util.Logger
import binny.Binary.Implicits._
import binny.jdbc._
import cats.effect.IO
import cats.effect.unsafe.implicits._

val dataSource = ConnectionConfig.h2Memory("docs").dataSource
implicit val logger = Logger.silent[IO]
val store = GenericJdbcStore.default[IO](dataSource, Logger.silent[IO])

// creates the schema, the filenames are from the default config
DatabaseSetup.runBoth[IO](Dbms.H2, dataSource, "file_chunk", "file_attr").unsafeRunSync()

val someData = ExampleData.helloWorld[IO]
val id = someData.through(store.insert(Hint.none)).compile.lastOrError.unsafeRunSync()

// get the file out
store.findBinary(id, ByteRange.All).getOrElse(sys.error("not found"))
  .flatMap(binary => binary.readUtf8String.compile.lastOrError)
  .unsafeRunSync()
```

The default setup also stores the attributes in the same database in a
different table.

```scala mdoc
store.findAttr(id).getOrElse(sys.error("not found")).unsafeRunSync()
```


## JdbcBinaryStore

As seen above, the store is an instance of the trait
`JdbcBinaryStore`. It extends `BinaryStore` to add a
`findBinaryStateful` method. The "state" relates to the connection to
the database.

The default `findBinary` method uses one connection per chunk. This
allows to free resources each time the stream is pulled. Otherwise
timeouts could occur, if for example the stream is not being pulled
for a while. When a network client is consuming the stream with a slow
connection, reading one chunk takes a while and could lead to the
connection being closed (by the pool or server).

However, if you know to process only small files or consume the data
fast, it is possible to stream the whole file using a single
connection, which is faster. This is provided by `findBinaryStateful`
(not happy with the name…).


## JdbcAttributeStore

The `JdbcAttributeStore` can be used to store the `BinaryAttributes`
in the database. If your application uses a sql database, it might be
useful to have the attributes in the database to build on it. The data
can be stored somewhere else, if desired. The `JdbcAttributeStore` can
be used with different `BinaryStore` implementations.
