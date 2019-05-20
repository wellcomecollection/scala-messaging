package uk.ac.wellcome.messaging.sns

import com.amazonaws.services.sns.model.AmazonSNSException
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.messaging.fixtures.SNS
import uk.ac.wellcome.messaging.fixtures.SNS.Topic

import scala.util.{Failure, Success}

class SNSMessageSenderTest extends FunSpec with Matchers with SNS {
  it("sends messages to SNS") {
    withLocalSnsTopic { topic =>
      val sender = new SNSIndividualMessageSender(snsClient)

      sender.send("hello world")(
        subject = "Sent from SNSMessageSenderTest",
        destination = createSNSConfigWith(topic)
      ) shouldBe Success(())

      listMessagesReceivedFromSNS(topic).map { _.message } shouldBe Seq("hello world")
    }
  }

  it("fails if it cannot send to SNS") {
    val sender = new SNSIndividualMessageSender(snsClient)

    val result = sender.send("hello world")(
      subject = "Sent from SNSMessageSenderTest",
      destination = createSNSConfigWith(Topic("does not exist"))
    )

    result shouldBe a[Failure[_]]
    val err = result.failed.get
    err shouldBe a[AmazonSNSException]
    err.getMessage should startWith("Unknown topic: does not exist")
  }
}
