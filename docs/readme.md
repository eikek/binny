<a href="https://eikek.github.io/binny/">
  <img height="120" align="right" style="float:right" src="./modules/microsite/src/main/resources/microsite/img/logo.png">
</a>

# binny

[![Scaladex](https://index.scala-lang.org/eikek/binny/latest.svg?color=blue)](https://index.scala-lang.org/eikek/binny/binny-core)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Binny is a Scala library for dealing with files based on fs2/cats.

## Motivation

Many applications need to deal with some kind of files. There are
maybe user files, temporary files, important documents etc and it's
very often quite tedious to manage them. Many database systems are not
made for storing large files, so people reach out to plain file
systems, which have their own issues. There is some compromise to
make. Either way, it often involves boilerplate code. Binny aims to
make it more convenient by providing a simple api for storing and
retrieving files with a few implementations. The idea is to pick the
storage backend that fits most, while user code can deal with the same
unified (and simplified) api.

The use cases it addresses are:

1. Having some binary data, store it somewhere
2. Having a reference, load it from somewhere

One thing that is explicitly not a goal is searching for files by
attributes. This should be handled by the application. The simplified
api should make it possible to create more backends without much
effort. It is modelled after a key-value store, where the value is a
potentially large binary.

## Pointers

- https://jdbc.postgresql.org/documentation/head/binary-data.html
- https://www.postgresql.org/docs/current/lo.html
