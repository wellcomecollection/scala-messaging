package uk.ac.wellcome.messaging

import io.circe.Decoder
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.message.RemoteNotification
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.memory.MemoryObjectStore

import scala.util.Success

class BigMessageReaderTest extends FunSpec with Matchers {
  case class Shape(colour: String, sides: Int)

  val blueTriangle = Shape(colour = "green", sides = 3)

  it("reads a large message from the object store") {
    val store = new MemoryObjectStore[Shape]()

    val reader = new BigMessageReader[Shape] {
      override val objectStore: ObjectStore[Shape] = store
      override implicit val decoder: Decoder[Shape] = Decoder[Shape]
    }

    val location = store.put(namespace = "shapes")(blueTriangle).get
    val notification = RemoteNotification(location)

    reader.read(notification) shouldBe Success(blueTriangle)
  }
}
