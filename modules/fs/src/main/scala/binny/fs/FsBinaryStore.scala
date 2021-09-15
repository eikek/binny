package binny.fs

import binny._
import binny.util.{Logger, Stopwatch}
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.Pipe
import fs2.Stream
import fs2.io.file.{Files, Path}

final class FsBinaryStore[F[_]: Async](
    val config: FsStoreConfig,
    logger: Logger[F],
    attrStore: BinaryAttributeStore[F]
) extends BinaryStore[F] {
  def insert(hint: ContentTypeDetect.Hint): Pipe[F, Byte, BinaryId] =
    in =>
      Stream
        .eval(BinaryId.random)
        .flatMap(id => in.through(insertWith(id, hint)) ++ Stream.emit(id))

  def insertWith(id: BinaryId, hint: ContentTypeDetect.Hint): Pipe[F, Byte, Nothing] =
    bytes =>
      {
        val target = config.targetFile(id)

        // tried with .observe to consume the stream once, but it took 5x longer
        val attr = Files[F]
          .readAll(target)
          .through(BinaryAttributes.compute(config.detect, hint))
          .compile
          .lastOrError

        val storeFile =
          bytes.through(Impl.write[F](target, config.overwriteMode)) ++ Stream.eval(
            attrStore.saveAttr(id, attr)
          )

        for {
          _ <- logger.s.debug(s"Insert file with id ${id.id}")
          w <- Stream.eval(Stopwatch.start[F])
          _ <- storeFile
          _ <- Stream.eval(
            Stopwatch.show(w)(d => logger.debug(s"Inserting file ${id.id} took $d"))
          )
        } yield ()
      }.drain

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] = {
    val target = config.targetFile(id)
    Impl.load[F](target, range, config.chunkSize)
  }

  def delete(id: BinaryId): F[Unit] = {
    val target = config.targetFile(id)
    attrStore.deleteAttr(id) *> Impl.delete[F](target).map(_ => ())
  }

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    attrStore.findAttr(id)
}

object FsBinaryStore {

  def apply[F[_]: Async](
      config: FsStoreConfig,
      logger: Logger[F],
      attrStore: BinaryAttributeStore[F]
  ): FsBinaryStore[F] =
    new FsBinaryStore[F](config, logger, attrStore)

  def apply[F[_]: Async](
      logger: Logger[F],
      storeCfg: FsStoreConfig,
      attrCfg: FsAttrConfig
  ): FsBinaryStore[F] = {
    val attrStore = FsAttributeStore(attrCfg)
    new FsBinaryStore[F](storeCfg, logger, attrStore)
  }

  def default[F[_]: Async](logger: Logger[F], baseDir: Path): FsBinaryStore[F] =
    apply(logger, FsStoreConfig.default(baseDir), FsAttrConfig.default(baseDir))
}
