package uk.ac.wellcome.messaging.worker.steps

import java.time.Instant

import org.scalatest.FunSpec
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures

class MessageDeserialiserTest extends FunSpec with WorkerFixtures {
  it("calls transform function and returns result") {
    val now = Instant.now
    val messageTransform =
      new MessageDeserialiser[MyMessage, MyWork, MyContext] {
        override val deserialise: MyMessage => Transformed = _ => {
          (Right(work), Right(Some(now)))
        }
      }

    messageTransform.callDeserialise(message) shouldBe (
      (
        Right(work),
        Right(Some(now))))
  }

  it("returns Left if transform function throws an exception") {
    val exception = new RuntimeException

    val messageTransform =
      new MessageDeserialiser[MyMessage, MyWork, MyContext] {
        override val deserialise: MyMessage => Transformed = _ => {
          throw exception
        }
      }

    messageTransform.callDeserialise(message) shouldBe (
      (
        Left(exception),
        Left(exception)))
  }
}
