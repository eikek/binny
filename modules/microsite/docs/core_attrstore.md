---
layout: docs
title: Core Api - BinaryAttributeStore
permalink: core/attrstore
---

# BinaryAttributeStore

The case class `BinaryAttributes` describes some attributes that are
derived from binary data: its sha256 hash, its length and its content
type. In theory this can be re-created by reading through all
binaries.

The `BinaryAttributeStore` trait defines methods to store and
retrieve these attributes given a `BinaryId`.

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
implementation to ignore the computation of this data. All
`BinaryStore` implementations take one of this as argument. You can
always pass `BinaryAttributeStore.none` to not maintain attributes.
