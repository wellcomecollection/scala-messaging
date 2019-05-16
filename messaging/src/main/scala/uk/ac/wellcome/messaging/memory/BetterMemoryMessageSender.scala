package uk.ac.wellcome.messaging.memory

import uk.ac.wellcome.messaging.BetterMessageSender

import scala.util.Try

class BetterMemoryMessageSender(val defaultDestination: String) extends BetterMessageSender[String] {
  case class MessageBody(message: String, subject: String)
  case class Message(body: MessageBody, destination: String)

  var messages: List[Message] = List.empty

  override def send(message: String, subject: String, destination: String = defaultDestination): Try[Unit] = Try {
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
