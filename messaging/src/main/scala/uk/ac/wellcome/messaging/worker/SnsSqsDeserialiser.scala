package uk.ac.wellcome.messaging.worker

import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.worker.steps.MessageDeserialiser

class SnsSqsDeserialiser[Payload](implicit decoder: Decoder[Payload])
    extends MessageDeserialiser[SQSMessage, Payload, Map[String, String]] {

  type SQSTransform = SQSMessage => Deserialised

  implicit val nd = implicitly[Decoder[NotificationMessage]]

  final def deserialise(message: SQSMessage): Deserialised = {
    val notificationMessage = fromJson[NotificationMessage](message.getBody)
    val deserialised = for {
      notification <- notificationMessage
      work <- fromJson[Payload](notification.body)
      attributes = notification.MessageAttributes.mapValues(_.Value)
    } yield (work, attributes)
    deserialised.toEither
  }
}
