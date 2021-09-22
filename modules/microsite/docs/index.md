---
layout: home
position: 1
section: home
title: Home
technologies:
 - first: ["Scala", "Dealing with files"]
---

# Binny

Binny is a Scala library for efficiently storing and retrieving
(large) binary data from databases, providing a
[FS2](https://github.com/functional-streams-for-scala/fs2) based api.

Binny is provided for Scala 2.13 and 3.

## Usage

With [sbt](https://scala-sbt.org), add the dependencies:

```
"com.github.eikek" %% "binny-core" % "@VERSION@"  // the core library
// … choose one or more implementation modules
"com.github.eikek" %% "binny-fs" % "@VERSION@"  // implementation based on FS2 `Files` api
"com.github.eikek" %% "binny-jdbc" % "@VERSION@"  // implementation based on JDBC
"com.github.eikek" %% "binny-pglo" % "@VERSION@"  // implementation based on PostgreSQLs LargeObject API
"com.github.eikek" %% "binny-minio" % "@VERSION@"  // implementation for MinIO
```




## License

This project is distributed under the
[MIT](https://spdx.org/licenses/MIT)

The logo is from
[here](https://openclipart.org/download/256586/1469591207.svg).
