package binny.minio

import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.GenericContainer.DockerImage
import org.testcontainers.containers.wait.strategy.Wait

object MinioContainer {

  val container: GenericContainer = GenericContainer(
    dockerImage = DockerImage(Left("quay.io/minio/minio")),
    exposedPorts = Seq(9000),
    env = Map("MINIO_ROOT_USER" -> "root", "MINIO_ROOT_PASSWORD" -> "d2Fscm/f"),
    command = Seq("server", "/data"),
    waitStrategy = Wait.forListeningPort()
  )

  object Def extends GenericContainer.Def[GenericContainer](container) {}
}
