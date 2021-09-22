---
layout: docs
title: Core Api - BinaryAttributeStore
permalink: core/attrstore
---

# BinaryAttributeStore

The case class `BinaryAttributes` describes some attributes that can
be derived from binary data: sha256 hash, length and content type. In
theory this can be re-created by reading through all binaries.

The `BinaryAttributeStore` trait defines methods to store and
retrieve these attributes given a `BinaryId`.

``` scala
trait BinaryAttributeStore[F[_]] {
  /** Associate the attributes to the key. If already exists, the data is replaced. */
  def saveAttr(id: BinaryId, attrs: F[BinaryAttributes]): F[Unit]

  /** Removes the attributes associated to this id, if existing. */
  def deleteAttr(id: BinaryId): F[Boolean]

  /** Finds attributes by its id. */
  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes]
}
```

Saving attributes takes a `F[BinaryAttributes]` to allow an "empty"
implementation to ignore the computation of this data. All
`BinaryStore` implementations take one of this as argument. You can
always pass `BinaryAttributeStore.none` to not maintain attributes.

A `BinaryStore` can create attributes when storing a byte stream. Many
implementations here require a `BinaryAttributeStore` - but it's
optional, since `BinaryAttributeStore.empty` can be specified.

Attributes can be retrieved separately from the data. Both, the bytes
and the attributes, may be stored at different places. For example,
[MinIO](minio) could be used to store the blobs while a sql database
could be used to store the attributes. The attributes table can then
be integrated in applications properly, using libraries like slick or
doobie.
