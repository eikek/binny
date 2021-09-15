---
layout: docs
position: 30
title: PGLO
permalink: pglo
---

# PGLO module

This module utilises PostgreSQLs [Large
Objects](https://www.postgresql.org/docs/current/largeobjects.html) to
implement a `BinaryStore`. This is then only for PostgreSQL, and it
also depends on the postgresql jdbc driver.

Using large objects, postgresql stores the data outside its standard
table space and the object can be referenced by an id. They also allow
to seek into a specific position, which is used to implement loading
partial data.

The `PgLoBinaryStore` also implements `JdbcBinaryStore` and provides
two methods to retrieve a binary. One, the default `findBinary`, uses
one connection per chunk. The other, `findBinaryStateful` uses a
connection for the entire stream.
