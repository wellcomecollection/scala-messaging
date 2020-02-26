package uk.ac.wellcome.messaging.worker

import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.worker.steps.MessageDeserialiser

class SnsSqsDeserialiser[Payload, MonitoringContext](
  implicit decoder: Decoder[Payload])
    extends MessageDeserialiser[SQSMessage, Payload, MonitoringContext] {

  type SQSTransform = SQSMessage => Transformed

  implicit val nd = implicitly[Decoder[NotificationMessage]]

  final def deserialise(message: SQSMessage): Transformed = {
    val f = for {
      notification <- fromJson[NotificationMessage](message.getBody)
      work <- fromJson[Payload](notification.body)
    } yield work
    (f.toEither, Right(None))
  }
}
