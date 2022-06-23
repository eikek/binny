package binny.fs

import binny._
import binny.util.{Logger, Stopwatch}
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits._
import fs2.Pipe
import fs2.Stream
import fs2.io.file.{Files, Path}

final class FsBinaryStore[F[_]: Async](
    val config: FsStoreConfig,
    logger: Logger[F]
) extends BinaryStore[F] {
  def insert: Pipe[F, Byte, BinaryId] =
    in =>
      Stream
        .eval(BinaryId.random)
        .flatMap(id => in.through(insertWith(id)) ++ Stream.emit(id))

  def insertWith(id: BinaryId): Pipe[F, Byte, Nothing] =
    bytes =>
      {
        val target = config.targetFile(id)

        val storeFile =
          bytes.through(Impl.write[F](target, config.overwriteMode))

        for {
          _ <- logger.s.debug(s"Insert file with id ${id.id}")
          w <- Stream.eval(Stopwatch.start[F])
          _ <- storeFile ++ Stream.eval(
            Stopwatch.show(w)(d => logger.debug(s"Inserting file ${id.id} took $d"))
          )
        } yield ()
      }.drain

  def computeAttr(id: BinaryId, hint: Hint): ComputeAttr[F] =
    Kleisli { select =>
      val file = config.targetFile(id)
      OptionT.liftF(Files[F].exists(file)).filter(identity).as(select).semiflatMap {
        case AttributeName.ContainsSha256(_) =>
          Files[F]
            .readAll(config.targetFile(id))
            .through(ComputeAttr.computeAll(config.detect, hint))
            .compile
            .lastOrError

        case AttributeName.ContainsLength(_) =>
          val length = Files[F].size(file)
          val ct = Impl.detectContentType(file, config.detect, hint)
          (ct, length).mapN(BinaryAttributes.apply)

        case _ =>
          val ct = Impl.detectContentType(file, config.detect, hint)
          ct.map(ctype => BinaryAttributes.empty.copy(contentType = ctype))
      }
    }

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] = {
    val target = config.targetFile(id)
    Impl.load[F](target, range, config.chunkSize)
  }

  def exists(id: BinaryId) = {
    val target = config.targetFile(id)
    Files[F].exists(target)
  }

  def delete(id: BinaryId): F[Unit] = {
    val target = config.targetFile(id)
    Impl.delete[F](target).map(_ => ())
  }

  def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] = {
    val all = Files[F]
      .walk(config.baseDir)
      .evalFilter(p => Files[F].isRegularFile(p))
      .mapFilter(p => config.mapping.idFromFile(p))

    prefix.map(p => all.filter(_.id.startsWith(p))).getOrElse(all)
  }
}

object FsBinaryStore {
  def apply[F[_]: Async](
      config: FsStoreConfig,
      logger: Logger[F]
  ): FsBinaryStore[F] =
    new FsBinaryStore[F](config, logger)

  def apply[F[_]: Async](
      logger: Logger[F],
      storeCfg: FsStoreConfig
  ): FsBinaryStore[F] =
    new FsBinaryStore[F](storeCfg, logger)

  def default[F[_]: Async](logger: Logger[F], baseDir: Path): FsBinaryStore[F] =
    apply(logger, FsStoreConfig.default(baseDir))
}
