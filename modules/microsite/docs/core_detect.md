---
layout: docs
title: Core API - ContentTypeDetect
permalink: core/detect
---


# ContentTypeDetect

The content type of a binary is always an interesting thing to know.
It is (kind of) required when serving files from a http server. The
content type is part of the `BinaryAttributes`.

The `ContentTypeDetect` trait exists to plug in a detector for the
content type. The `detect` method receives a portion of the data as a
`ByteVector` and some hints - either a filename and/or an advertised
content type.

You can just use a `ContentTypeDetect.none` if you don't bother.

The core module provides an implementation based on the
[`FileTypeDetector`](https://docs.oracle.com/javase/8/docs/api/java/nio/file/spi/FileTypeDetector.html)
api present since Java7. This only looks at the filename if present.
If no filename is given in the `hint`, it will always return
`application/octet-stream`.

```scala mdoc
import binny._
import binny.ContentTypeDetect.Hint
import scodec.bits.ByteVector

ContentTypeDetect.probeFileType
  .detect(ByteVector.empty, Hint.filename("index.html"))
```

Note, the result above depends on the runtime. It can be modified by
other jars in the class path, for example. For more reliable results,
the `binny-tikadetect` module is recommended.

## Tika

For a better outcome, the module `binny-tikadetect` provides a
`ContentTypeDetect` implemenatiton based on the well known
[tika](https://tika.apache.org/) library. This also looks at the given
bytes in conjunction with the provided hints.

```scala mdoc
import binny._
import binny.tika._
import binny.ContentTypeDetect.Hint
import scodec.bits.ByteVector

// using the filename only
TikaContentTypeDetect.default
  .detect(ByteVector.empty, Hint.filename("index.html"))

// using the first view bytes
TikaContentTypeDetect.default
  .detect(ByteVector.fromValidBase64("iVBORw0KGgoAAAANSUhEUgAAA2I="), Hint.none)
```
