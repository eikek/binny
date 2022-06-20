package binny.minio

import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.GenericContainer.DockerImage
import org.testcontainers.containers.wait.strategy.Wait

class MinioContainer(port: Int, val accessKey: String, val secretKey: String)
    extends GenericContainer(
      dockerImage = DockerImage(Left("quay.io/minio/minio")),
      exposedPorts = Seq(port),
      env = Map(
        "MINIO_ROOT_USER" -> accessKey,
        "MINIO_ROOT_PASSWORD" -> secretKey
      ),
      command = Seq("server", "/data"),
      waitStrategy = Some(Wait.forHttp("/minio/health/live")),
      labels = Map.empty,
      tmpFsMapping = Map.empty,
      imagePullPolicy = None,
      fileSystemBind = Seq.empty,
      startupCheckStrategy = None
    ) {

  def endpoint =
    s"http://${underlyingUnsafeContainer.getHost}:${underlyingUnsafeContainer.getMappedPort(port)}"

  def createConfig(m: S3KeyMapping): MinioConfig =
    MinioConfig.default(endpoint, accessKey, secretKey, m)

  override def toString =
    s"MinioContainer($endpoint, accessKey=$accessKey)"
}

object MinioContainer {
  val accessKey = "root"
  val secretKey = "d2Fscm/f"
  private val port = 9000

  final class Def
      extends GenericContainer.Def[MinioContainer](
        new MinioContainer(port, accessKey, secretKey)
      )
}
