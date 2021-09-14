---
layout: docs
position: 10
section: home
title: Core API
---

# Core API

The core module depends on the [fs2](https://fs2.io/) library to
provide a convenient api.

The main idea is to have a convenient and uniform api for
storing/retrieving files across a variety of backends. The most focus
is on sql databases. The [jdbc](jdbc.html) and [pglo](pglo.html)
module aim to provide an efficient way to store even large files in
databases.

## BinaryStore

The `BinaryStore` trait is the main entrypoint and defines very basic
methods for storing and retrieving data. The binary data is
represented as a `Stream[F, Byte]` which can be referred to via a
`BinaryId`.

``` scala
trait BinaryStore[F[_]] extends ReadonlyAttributeStore[F] {
  /** Insert the given bytes creating a new id. */
  def insert(hint: Hint): Pipe[F, Byte, BinaryId]

  /** Insert the given bytes to the given id. If some file already exists by
    * this id, the behavior depends on the implementation.
    */
  def insertWith(id: BinaryId, hint: Hint): Pipe[F, Byte, Nothing]

  /** Finds a binary by its id. The range argument controls which part to return. */
  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]]

  /** Deletes all data associated to the given id. */
  def delete(id: BinaryId): F[Unit]

  // inherited from ReadonlyAttributeStore
  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes]
}
```

It is only capable of storing, deleting and retrieving data and
attributes. Listing/finding files is not supported.

Note that the `findBinary` allows to specify a range in order to
retrieve only a part of some file.

The `BinaryAttributes` are attributes derived from the data itself (it
is not user supplied data). In theory this can be re-created by
reading all binaries. It currently is: the length, the sha256 hash and
the content type. The content type is optional and could be
`application/octet-stream`. A `BinaryStore` can create these
attributes when storing files. It therefore extends
`ReadOnlyAttributeStore` in order to retrieve them. Attributes can be
retrieved separately from the data. Both, the bytes and the
attributes, may be stored at different places. For example, MinIO
could be used to store the blobs while a sql database could be used to
store the attributes. The attributes table can be integrated in an
application that uses libraries like slick or doobie to work with SQL.


## BinaryAttributesStore

This trait defines the two methods to store and retrieve attributes to
an id.

``` scala
trait BinaryAttributeStore[F[_]] extends ReadonlyAttributeStore[F] {
  /** Associate the attributes to the key. If already exists, the data is replaced. */
  def saveAttr(id: BinaryId, attrs: F[BinaryAttributes]): F[Unit]

  /** Removes the attributes associated to this id, if existing. */
  def deleteAttr(id: BinaryId): F[Boolean]

  // inherited from ReadonlyAttributeStore
  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes]
}
```

Saving attributes takes a `F[BinaryAttributes]` to allow an "empty"
implementation to ignore the creating of this data. All `BinaryStore`
implementations take one of this as argument. You can always pass
`BinaryAttributesStore.none` to not maintain attributes.



## ContentTypeDetect

The content type of a binary is always an interesting thing to know.
It is (kind of) required when serving files from a web server. The
content type is part of the `BinaryAttributes`.

The `ContentTypeDetect` trait exists to plugin a detector for the
content type. The `detect` method receives a portion of the data as a
`ByteVector` and some hints - either a filename and/or an advertised
content type.

The core module provides an implementation based on the
[`FileTypeDetector`](https://docs.oracle.com/javase/8/docs/api/java/nio/file/spi/FileTypeDetector.html)
api present since Java7. This only looks at the filename if present.
If no filename is given in the `hint`, it will always return
`application/octet-stream`.

### Tika

For a better outcome, the module `binny-tikadetect` provide
`ContentTypeDetect` implemenatiton based on the well known
[tika](https://tika.apache.org/) library. This also looks at the given
bytes in conjunction with the provided hints.


## Implementations

The different implementations follow a similar pattern: There is some
config case class and a backend object required (like a
`javax.sql.DataSource` for example) in order to create a
`BinaryStore`.

Note that the config can define how the data is stored. If it is
changed after data has been stored, the new store may not be
compatible to previously written data.

Typically, modules provide a `BinaryStore` and `BinaryAttributesStore`
implementation that you can mix.
