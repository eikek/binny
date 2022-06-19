---
layout: docs
title: Core Api - Chunked Binary Store
permalink: core/chunkedstore
---

# ChunkedBinaryStore

The `ChunkedBinaryStore` trait extends `BinaryStore` and adds one
method:


``` scala
/** A BinaryStore that can also store chunks out of order. */
trait ChunkedBinaryStore[F[_]] extends BinaryStore[F] {

  /** Inserts the given chunk. This method allows to store chunks out of order. It is
    * required to specify the total amount of chunks; thus the total length of the file
    * must be known up front.
    *
    * The first chunk starts at index 0.
    *
    * The maximum chunk size is constant and defined by the implementation. All chunks
    * must not exceed this length. If the complete file consists of multiple chunks, then
    * only the last one may have less than this size.
    */
  def insertChunk(
      id: BinaryId,
      chunkDef: ChunkDef,
      hint: Hint,
      data: ByteVector
  ): F[InsertChunkResult]
}
```

This allows storing a file in chunks and these chunks may arrive out
of order. To have them concatenated correctly to the whole file, the
chunk number must be provided and either the complete file length or
the total amount of chunks.

Not all implementation modules provide this type of store.
