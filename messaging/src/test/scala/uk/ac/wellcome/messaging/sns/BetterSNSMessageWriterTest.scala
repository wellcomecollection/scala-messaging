package uk.ac.wellcome.messaging.sns

import com.amazonaws.services.sns.model.AmazonSNSException
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.messaging.fixtures.SNS
import uk.ac.wellcome.messaging.fixtures.SNS.Topic

import scala.util.{Failure, Success}

class BetterSNSMessageWriterTest extends FunSpec with Matchers with SNS {
  it("sends a message to SNS") {
    withLocalSnsTopic { topic =>
      withBetterSNSMessageWriter(topic) { writer =>
        val result = writer.send(
          message = "hello world",
          subject = "my first message"
        )

        result shouldBe Success(())

        val receivedMessages = listMessagesReceivedFromSNS(topic)
        receivedMessages.size shouldBe 1

        val message = receivedMessages.head
        message.subject shouldBe "my first message"
        message.message shouldBe "hello world"
      }
    }
  }

  it("fails if it cannot send to SNS") {
    withBetterSNSMessageWriter(Topic("does not exist")) { writer =>
      val result = writer.send(
        message = "hello world",
        subject = "my first message"
      )

      result shouldBe a[Failure[_]]
      result.failed.get shouldBe a[AmazonSNSException]
      result.failed.get.getMessage should startWith("Unknown topic")
    }
  }
}
