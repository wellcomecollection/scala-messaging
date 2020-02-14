package uk.ac.wellcome.messaging.worker.steps

import java.time.Instant

import org.scalatest.FunSpec
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures

class MessageTransformTest extends FunSpec with WorkerFixtures {
  it("calls transform function and returns result") {
    val messageTransform = new MessageTransform[MyMessage, MyWork, MyContext] {
      override val transform: MyMessage => Transformed = _ => {
        (Right(work), Right(Some(Instant.now)))
      }
    }

    messageTransform.callTransform(message) shouldBe ((Right(work), Right(Some(Instant.now))))
  }

  it("returns Left if transform function throws an exception") {
    val exception = new RuntimeException

    val messageTransform = new MessageTransform[MyMessage, MyWork, MyContext] {
      override val transform: MyMessage => Transformed = _ => {
        throw exception
      }
    }

    messageTransform.callTransform(message) shouldBe ((Left(exception), Left(exception)))
  }
}
