package uk.ac.wellcome.messaging.memory

import uk.ac.wellcome.messaging.BetterMessageWriter

import scala.util.Try

class BetterMemoryMessageWriter extends BetterMessageWriter[String] {
  override val defaultDestination: String = "defaultDestination"

  case class MessageBody(message: String, subject: String)
  case class Message(body: MessageBody, destination: String)

  var messages: List[Message] = List.empty

  override def writeMessage(message: String, subject: String, destination: String): Try[Unit] = Try {
    messages = messages :+ Message(
      body = MessageBody(message, subject),
      destination = destination
    )
  }

  def getMessagesTo(destination: String): Seq[MessageBody] =
    messages
      .filter { _.destination == destination }
      .map { _.body }
}
