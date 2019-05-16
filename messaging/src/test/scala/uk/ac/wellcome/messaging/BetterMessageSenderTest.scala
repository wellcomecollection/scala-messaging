package uk.ac.wellcome.messaging

import io.circe.{Encoder, Json}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.messaging.memory.BetterMemoryMessageSender

import scala.util.{Failure, Success}

class BetterMessageSenderTest extends FunSpec with Matchers with JsonAssertions {
  it("sends to the default destination") {
    val destination = "red-messages"

    val sender = new BetterMemoryMessageSender(
      defaultDestination = destination
    )

    sender.send(
      message = "hello world",
      subject = "my first message"
    ) shouldBe Success(())

    sender.messages shouldBe List(
      sender.Message(
        body = sender.MessageBody("hello world", "my first message"),
        destination = destination
      )
    )
  }

  it("allows you to set an alternative destination") {
    val sender = new BetterMemoryMessageSender(
      defaultDestination = "red-messages"
    )

    sender.send(
      message = "howdy folks",
      subject = "another message",
      destination = "green-messages"
    ) shouldBe Success(())

    sender.messages shouldBe List(
      sender.Message(
        body = sender.MessageBody("howdy folks", "another message"),
        destination = "green-messages"
      )
    )
  }

  case class Shape(sides: Int, color: String)

  describe("encoding a case class as JSON") {
    it("sends the message") {
      val sender = new BetterMemoryMessageSender(
        defaultDestination = "yellow-messages"
      )

      val square = Shape(sides = 4, color = "yellow")

      sender.sendT(
        square,
        subject = "my yellow square"
      ) shouldBe Success(())

      val body = sender.messages.head.body.message
      assertJsonStringsAreEqual(body, toJson(square).get)
    }

    it("fails if it the encoding fails") {
      val sender = new BetterMemoryMessageSender(
        defaultDestination = "yellow-messages"
      )

      val triangle = Shape(sides = 3, color = "purple")

      val brokenEncoder = new Encoder[Shape] {
        override def apply(a: Shape): Json = throw new RuntimeException("BOOM!")
      }

      val result = sender.sendT(triangle, subject = "my purple triangle")(encoder = brokenEncoder)

      result shouldBe a[Failure[_]]
      result.failed.get shouldBe a[RuntimeException]
      result.failed.get.getMessage shouldBe "BOOM!"
    }
  }
}
