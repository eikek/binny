package binny.minio

import binny.BinaryId
import munit.FunSuite

class S3KeyMappingTest extends FunSuite {

  test("constant mapping") {
    val m = S3KeyMapping.constant("my-bucket")
    assertEquals(m.makeS3Key(bid("abc")).bucket, "my-bucket")
    assertEquals(m.makeS3Key(bid("abc")).objectName, "abc")
    assert(m.bucketFilter("my-bucket"))
    assert(!m.bucketFilter("bucket"))
  }

  test("prefix mapping") {
    val m = S3KeyMapping.prefix("myapp", '/')

    assertEquals(m.makeS3Key(bid("user1/file1")).bucket, "myapp-user1")
    assertEquals(m.makeS3Key(bid("user1/file1")).objectName, "user1/file1")
    assertEquals(m.makeS3Key(bid("user1/file2")).bucket, "myapp-user1")
    assertEquals(m.makeS3Key(bid("user2/file1")).bucket, "myapp-user2")
    assertEquals(m.makeS3Key(bid("user2/file1/part0")).bucket, "myapp-user2")
    assertEquals(m.makeS3Key(bid("/file1")).bucket, "myapp")
    assertEquals(m.makeS3Key(bid("file43")).bucket, "myapp")
    assert(m.bucketFilter("myapp"))
    assert(m.bucketFilter("myapp-"))
    assert(m.bucketFilter("myapp-user1"))
    assert(m.bucketFilter("myapp-user2"))
  }

  def bid(str: String): BinaryId = BinaryId(str)
}
