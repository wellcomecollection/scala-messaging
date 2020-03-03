package uk.ac.wellcome.messaging.worker.steps

import org.scalatest.FunSpec
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures

class MessageDeserialiserTest extends FunSpec with WorkerFixtures {
  it("calls transform function and returns result") {
    val messageTransform =
      new MessageDeserialiser[MyMessage, MyPayload, MyMessageMetadata] {
        override def deserialise(msg: MyMessage): Deserialised =
          (Right((work,Map[String,String]())))
      }

    messageTransform(message) shouldBe (Right((work, Map[String,String]())))
  }

  it("returns Left if transform function throws an exception") {
    val exception = new RuntimeException

    val messageTransform =
      new MessageDeserialiser[MyMessage, MyPayload, MyTrace] {
        override def deserialise(msg: MyMessage): Deserialised =
          throw exception
      }

    messageTransform(message) shouldBe (Left(exception))
  }
}
