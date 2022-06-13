package binny.minio

import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.GenericContainer.DockerImage
import org.testcontainers.containers.wait.strategy.Wait

object MinioContainer {

  val accessKey = "root"
  val secretKey = "d2Fscm/f"
  private val port = 9000

  private def container: GenericContainer = GenericContainer(
    dockerImage = DockerImage(Left("quay.io/minio/minio")),
    exposedPorts = Seq(port),
    env = Map("MINIO_ROOT_USER" -> accessKey, "MINIO_ROOT_PASSWORD" -> secretKey),
    command = Seq("server", "/data"),
    waitStrategy = Wait.defaultWaitStrategy()
  )

  class Def extends GenericContainer.Def[MinioCnt](new MinioCnt()) {}

  final class MinioCnt extends GenericContainer(container) {

    def endpoint =
      s"http://${underlyingUnsafeContainer.getHost}:${underlyingUnsafeContainer.getMappedPort(port)}"
    val accessKey = MinioContainer.accessKey
    val secretKey = MinioContainer.secretKey

    def createConfig(m: S3KeyMapping): MinioConfig =
      MinioConfig.default(endpoint, accessKey, secretKey, m)
  }
}
