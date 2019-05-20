package uk.ac.wellcome.messaging.memory

import uk.ac.wellcome.messaging.{IndividualMessageSender, MessageSender}

import scala.util.Try

class MemoryIndividualMessageSender extends IndividualMessageSender[String] {
  case class MemoryMessage(
    body: String,
    subject: String,
    destination: String
  )

  var messages: List[MemoryMessage] = List.empty

  override def send(body: String)(subject: String,
                                  destination: String): Try[Unit] = Try {
    messages = messages :+ MemoryMessage(body, subject, destination)
  }
}

class MemoryMessageSender(
  val destination: String,
  val subject: String
) extends MessageSender[String] {
  override val underlying: MemoryIndividualMessageSender =
    new MemoryIndividualMessageSender()

  def messages: List[underlying.MemoryMessage] = underlying.messages
}
