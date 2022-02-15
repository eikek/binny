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
  /** Returns a set of ids currently available in this store. */
  def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId]

  /** Insert the given bytes creating a new id. */
  def insert(hint: Hint): Pipe[F, Byte, BinaryId]

  /** Insert the given bytes to the given id. If some file already exists by this id, the
    * behavior depends on the implementation.
    */
  def insertWith(id: BinaryId, hint: Hint): Pipe[F, Byte, Nothing]

  /** Finds a binary by its id. The range argument controls which part to return. */
  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]]

  /** Check if a file exists. Same as `findBinary().isDefined`, but usually more
    * efficiently implemented.
    */
  def exists(id: BinaryId): F[Boolean]

  /** Deletes all data associated to the given id. */
  def delete(id: BinaryId): F[Unit]
}
```

It is capable of storing, deleting and retrieving data and attributes.
Available ids can be listed, searching is not supported.

Note that the `findBinary` allows to specify a range in order to
retrieve only a part of some file.
