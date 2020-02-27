package uk.ac.wellcome.messaging.worker

import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.worker.monitoring.serialize.MonitoringContextDeserialiser
import uk.ac.wellcome.messaging.worker.steps.MessageDeserialiser

class SnsSqsDeserialiser[Payload, MonitoringContext](monitoringContextDeserialiser: MonitoringContextDeserialiser[MonitoringContext, Map[String, String]])(
  implicit decoder: Decoder[Payload])
    extends MessageDeserialiser[SQSMessage, Payload, MonitoringContext] {

  type SQSTransform = SQSMessage => Transformed

  implicit val nd = implicitly[Decoder[NotificationMessage]]

  final def deserialise(message: SQSMessage): Transformed = {
    val notificationMessage = fromJson[NotificationMessage](message.getBody)
    val payload = for {
      notification <- notificationMessage
      work <- fromJson[Payload](notification.body)
    } yield work


    val monitoringContext =for {
      notification <- notificationMessage
      attributes = notification.MessageAttributes.map(map => map.mapValues(_.Value))
      context <- monitoringContextDeserialiser.deserialise(attributes)
    } yield context
    (payload.toEither, monitoringContext.toEither)
  }
}
