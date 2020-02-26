package uk.ac.wellcome.messaging.worker.steps

import java.time.Instant

import org.scalatest.FunSpec
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures

class MessageDeserialiserTest extends FunSpec with WorkerFixtures {
  it("calls transform function and returns result") {
    val now = Instant.now
    val messageTransform =
      new MessageDeserialiser[MyMessage, MyPayload, MyContext] {
        override def deserialise(msg: MyMessage): Transformed =
          (Right(work), Right(Some(now)))
      }

    messageTransform(message) shouldBe (
      (
        Right(work),
        Right(Some(now))))
  }

  it("returns Left if transform function throws an exception") {
    val exception = new RuntimeException

    val messageTransform =
      new MessageDeserialiser[MyMessage, MyPayload, MyContext] {
        override def deserialise(msg: MyMessage): Transformed =
          throw exception
      }

    messageTransform(message) shouldBe (
      (
        Left(exception),
        Left(exception)))
  }
}
