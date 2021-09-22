---
layout: docs
position: 20
title: JDBC
permalink: jdbc
---

# Generic JDBC

This module provides a `BinaryStore` and `BinaryAttributeStore` using
JDBC. It is tested for H2, PostgreSQL and MariaDB only, but other
database systems probably work as well. The module doesn't include any
JDBC driver, you need to pull in the one you want to use in your
project.

The approach is as follows: the byte stream is split in chunks (size
can be configured) and each chunk is stored in a blob column and
associated to the same binary id.


## Table Structure

Since the blob datatype characteristics differ between databases, you
might want to define the table yourself or use the provided setup for
PostgreSQL or MariaDB. See the `CreateDataTable` for the table
definitions. Here is the definition for PostgreSQL:

``` sql
CREATE TABLE IF NOT EXISTS "file_chunk" (
  "file_id" varchar(254) not null,
  "chunk_nr" int not null,
  "chunk_len" int not null,
  "chunk_data" bytea not null,
  primary key ("file_id", "chunk_nr")
)
```

This makes range requests efficient, since it is possible to calculate
at what chunk to start (and to end if applicable).

The table names are just examples, they can be specified when creating
a store.


## Chunksize when storing and streaming

A caveat here is that the chunksize used to store a file, also
determines the amount of memory used when reading the file. It is not
possible to store in 512k chunks and then load it in 10k chunks, for
example. The reason is that many jdbc drivers (at least these I know)
don't support streaming from a blob. You'll get the whole blob in a
byte array anyways. So this cannot be changed after a file has been
stored. When streaming, the blob of each row is loaded in memory, one
at a time. Its size defines the amount of memory used to stream a
file.

If you're using PostgreSQL, consider the [pglo](pglo) module which
doesn't have this restriction.

## Usage

You need to provide a `javax.sql.DataSource`. How to create this is
out of scope for this project. A `JdbcStoreConfig` is required, that
defines some settings, like the table name and chunk size to use.

For the examples here, an in-memory database
([H2](https://h2database.com)) is used.

```scala mdoc
import binny._
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

val someData = ExampleData.file2M
val id = someData.through(store.insert(Hint.filename("test.txt")))
  .compile.lastOrError.unsafeRunSync()

// get the file out
store.findBinary(id, ByteRange.All).getOrElse(sys.error("not found"))
  .flatMap(binary => binary.readUtf8String)
  .unsafeRunSync()
  .take(50)
```

The default setup also stores the attributes in the same database in a
different table.

```scala mdoc
val attrStore = JdbcAttributeStore(JdbcAttrConfig.default, dataSource, logger)
attrStore.findAttr(id).getOrElse(sys.error("not found")).unsafeRunSync()
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
connection, which is faster. This is provided by `findBinaryStateful`.

## ChunkedBinaryStore

The `GenericJdbcStore` also implements `ChunkedBinaryStore` to allow
storing chunks independently. This is useful if chunks are received in
random order and the whole file is not available as complete stream.

However, in order to use this the complete size of the file must be
known up front. This is needed to know when the last chunk is
received.

## JdbcAttributeStore

The `JdbcAttributeStore` can be used to store `BinaryAttributes` in
the database. If your application uses a sql database, it might be
useful to have the attributes in the database to build on it. The data
can be stored somewhere else, if desired. The `JdbcAttributeStore` can
be used with different `BinaryStore` implementations.

Here is the table for storing attributes (PostgreSQL example):

```sql
CREATE TABLE IF NOT EXISTS "file_attr" (
  "file_id" varchar(254) not null,
  "sha256" varchar(254) not null,
  "content_type" varchar(254) not null,
  "length" bigint not null,
  primary key ("file_id")
)
```
