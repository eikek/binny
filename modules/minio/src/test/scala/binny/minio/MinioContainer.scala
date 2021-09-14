package binny.minio

import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.GenericContainer.DockerImage
import org.testcontainers.containers.wait.strategy.Wait

object MinioContainer {
  private val cfg = Config.testing(S3KeyMapping.constant("testing"), "", 0)

  def container: GenericContainer = GenericContainer(
    dockerImage = DockerImage(Left("quay.io/minio/minio")),
    exposedPorts = Seq(9000),
    env = Map("MINIO_ROOT_USER" -> cfg.accessKey, "MINIO_ROOT_PASSWORD" -> cfg.secretKey),
    command = Seq("server", "/data"),
    waitStrategy = Wait.defaultWaitStrategy()
  )

  class Def extends GenericContainer.Def[GenericContainer](container) {}
}
