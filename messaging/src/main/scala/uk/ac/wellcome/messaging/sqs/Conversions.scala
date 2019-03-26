package uk.ac.wellcome.messaging.sqs

import com.amazonaws.services.sqs.model.Message
import uk.ac.wellcome.messaging.worker.models.IdentifiedMessage

object Conversions {
  import scala.language.implicitConversions

  type IdMessage = IdentifiedMessage[Message]

  implicit class IdSQSMessage(message: Message) {
    def toIdMessage: IdMessage =
      IdentifiedMessage(message.getMessageId, message)
  }

  implicit def toIdMessage(message: Message): IdMessage = message.toIdMessage
}
