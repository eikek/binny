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

The `BinaryStore` trait defines very basic methods for storing and
retrieving data. The binary data is represented as a `Stream[F,
Byte]` which can be referred to via a `BinaryId`.

``` scala
trait BinaryStore[F[_]] extends ReadonlyStore[F] with ReadonlyAttributeStore[F] {

  def insertWith(data: BinaryData[F], hint: Hint): F[Unit]

  def delete(id: BinaryId): F[Boolean]

  // from ReadonlyStore
  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, BinaryData[F]]

  // from ReadonlyAttributestore
  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes]
}
```

It is only capable of storing, deleting and retrieving data.
Listing/finding files is not supported.

Note that the `findBinary` allows to specify a range in order to
retrieve only a part of some file.

Another part is the `BinaryAttributes` - every `BinaryStore` is
expected to create these attributes when storing files. It therefore
extends `ReadOnlyAttributeStore`. Attributes can be retrieved
separately from the data. Both, the bytes and the attributes, may be
stored at different places. For example, MinIO could be used to store
the blobs while a sql database could be used to store the attributes.

The `BinaryAttributes` only defines things that can be directly
generated from the data itself. It is not intended to store externally
defined data. It currently is: the length, the sha256 hash and the
content type. The content type is optional and could be
`application/octet-stream`.


## ContentTypeDetect

This trait exists to plugin a detector for the content type. The
`detect` method receives a portion of the data as a `ByteVector` and
some hints - either a filename and/or an advertised content type.

The core module provides an implementation based on the
[`FileTypeDetector`](https://docs.oracle.com/javase/8/docs/api/java/nio/file/spi/FileTypeDetector.html)
api present since Java7. This only looks at the filename if present.
If no filename is given in the `hint`, it always returns
`application/octet-stream`.

### Tika

For a better outcome, the module `binny-tikadetect` provide
`ContentTypeDetect` implemenatiton based on the well known
[tika](https://tika.apache.org/) library. This also looks at the given
bytes in conjunction with the provided hints.


## Implementations

The different implementations follow a similar pattern: You need to
create some kind of config object to configure the store and then an
instance can be created, additionally providing some backend
implementation class (like a `javax.sql.DataSource` for example).

Note that the config can define how the data is stored. If it is
changed after data has been stored, it may not be compatible to the
previous data.

Typically, modules provide a `BinaryStore` and `BinaryAttributesStore`
implementation that you can mix.
