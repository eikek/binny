---
layout: docs
title: Core Api - Binary Store
permalink: core/binarystore
---

# BinaryStore

The `BinaryStore` trait is the main entrypoint and defines very basic
methods for storing and retrieving data. The binary data is
represented as a `Binary[F]` (a type alias for `Stream[F, Byte]`)
which can be referred to via a `BinaryId`.

``` scala
trait BinaryStore[F[_]] {
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
}
```

It is capable of storing, deleting and retrieving data and attributes.
Listing/searching is not supported.

Note that the `findBinary` allows to specify a range in order to
retrieve only a part of some file.
