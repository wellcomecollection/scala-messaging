package uk.ac.wellcome.messaging.memory

import uk.ac.wellcome.messaging.{IndividualMessageSender, MessageSender}

import scala.util.{Success, Try}

class MemoryIndividualMessageSender extends IndividualMessageSender[String] {
  case class MemoryMessage(
    message: String,
    subject: String,
    destination: String
  )

  var messages: List[MemoryMessage] = List.empty

  override def send(message: String)(subject: String, destination: String): Try[Unit] = Try {
    messages = messages :+ MemoryMessage(message, subject, destination)
  }
}

class MemoryMessageSender(
  val destination: String,
  val subject: String
) extends MessageSender[String] {
  override protected val underlying: MemoryIndividualMessageSender =
    new MemoryIndividualMessageSender()

  def messages: List[underlying.MemoryMessage] = underlying.messages
}
