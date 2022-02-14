package binny.minio

import binny.BinaryId
import munit.FunSuite

class S3KeyMappingTest extends FunSuite {

  test("constant mapping") {
    val m = S3KeyMapping.constant("my-bucket")
    assertEquals(m.toBucket(BinaryId("abc")), "my-bucket")
    assert(m.bucketFilter("my-bucket"))
    assert(!m.bucketFilter("bucket"))
  }

  test("prefix mapping") {
    val m = S3KeyMapping.prefix("myapp", '/')

    assertEquals(m.toBucket(bid("user1/file1")), "myapp-user1")
    assertEquals(m.toBucket(bid("user1/file2")), "myapp-user1")
    assertEquals(m.toBucket(bid("user2/file1")), "myapp-user2")
    assertEquals(m.toBucket(bid("user2/file1/part0")), "myapp-user2")
    assertEquals(m.toBucket(bid("/file1")), "myapp")
    assertEquals(m.toBucket(bid("file43")), "myapp")
    assert(m.bucketFilter("myapp"))
    assert(m.bucketFilter("myapp-"))
    assert(m.bucketFilter("myapp-user1"))
    assert(m.bucketFilter("myapp-user2"))
  }

  def bid(str: String): BinaryId = BinaryId(str)
}
