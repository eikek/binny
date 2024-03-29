package binny.fs

import java.nio.file.{Files => NioFiles}

import binny._
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.io.file._
import fs2.{Pipe, Stream}

private[fs] object Impl {

  def write[F[_]: Async: Files](
      targetFile: Path,
      overwriteMode: OverwriteMode
  ): Pipe[F, Byte, Nothing] =
    bytes =>
      Stream.eval(Files[F].exists(targetFile)).flatMap {
        case true =>
          overwriteMode match {
            case OverwriteMode.Fail =>
              Stream
                .raiseError[F](new Exception(s"The file already exists: $targetFile"))

            case OverwriteMode.Skip =>
              Stream.empty.covary[F]

            case OverwriteMode.Replace =>
              bytes.through(Files[F].writeAll(targetFile))
          }
        case false =>
          val createDirs = targetFile.parent match {
            case Some(p) =>
              Files[F].createDirectories(p)
            case None =>
              Async[F].pure(())
          }
          Stream.eval(createDirs) >>
            bytes.through(Files[F].writeAll(targetFile))
      }

  def load[F[_]: Async: Files](
      targetFile: Path,
      range: ByteRange,
      chunkSize: Int
  ): OptionT[F, Binary[F]] =
    OptionT(Files[F].exists(targetFile).map {
      case true =>
        range match {
          case ByteRange.All =>
            Files[F].readAll(targetFile, chunkSize, Flags.Read).some

          case ByteRange.Chunk(start, len) =>
            Files[F].readRange(targetFile, chunkSize, start, start + len).some
        }

      case false =>
        None
    })

  def detectContentType[F[_]: Async: Files](
      file: Path,
      detect: ContentTypeDetect,
      hint: Hint
  ) =
    load(file, ByteRange.Chunk(0, 50), 50)
      .semiflatMap(_.through(detect.detectStream(hint)).compile.lastOrError)
      .getOrElse(SimpleContentType.octetStream)

  def delete[F[_]: Async: Files](targetFile: Path): F[Boolean] =
    Files[F]
      .deleteIfExists(targetFile)
      .recover { case _: NoSuchFileException =>
        true
      }

  def deleteDir[F[_]: Async: Files](dir: Path): F[Unit] =
    Files[F].exists(dir).flatMap {
      case true =>
        Files[F].deleteRecursively(dir)
      case false =>
        ().pure[F]
    }

  def writeAttrs[F[_]: Async: Files](file: Path, attrs: BinaryAttributes): F[Unit] =
    (Stream
      .eval(file.parent.map(Files[F].createDirectories).getOrElse(().pure[F]))
      .drain ++
      Stream
        .emit(BinaryAttributes.asString(attrs))
        .through(fs2.text.utf8.encode)
        .through(Files[F].writeAll(file))).compile.drain

  def loadAttrs[F[_]: Async: Files](file: Path): OptionT[F, BinaryAttributes] =
    OptionT(Files[F].exists(file).flatMap {
      case true =>
        Files[F]
          .readAll(file)
          .through(fs2.text.utf8.decode)
          .compile
          .string
          .flatMap { s =>
            if (s.isEmpty) None.pure[F]
            BinaryAttributes
              .fromString(s)
              .left
              .map(msg => new Exception(msg))
              .pure[F]
              .rethrow
              .map(_.some)
          }

      case false =>
        (None: Option[binny.BinaryAttributes]).pure[F]
    })

  def loadAll[F[_]: Sync](file: Path): F[Array[Byte]] =
    Sync[F].blocking(NioFiles.readAllBytes(file.toNioPath))

  def loadInto[F[_]: Sync](file: Path, buffer: Array[Byte]) = Sync[F].blocking {
    val in = NioFiles.newInputStream(file.toNioPath)
    in.read(buffer)
  }
}
