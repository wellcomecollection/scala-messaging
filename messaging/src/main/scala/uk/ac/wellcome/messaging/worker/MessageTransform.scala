package uk.ac.wellcome.messaging.worker

import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.worker.steps.MessageDeserialiser

trait SnsSqsDeserialiser[Work, MonitoringContext]
    extends MessageDeserialiser[SQSMessage, Work, MonitoringContext] {

  type SQSTransform = SQSMessage => Transformed

  implicit val nd = implicitly[Decoder[NotificationMessage]]
  implicit val wd: Decoder[Work]

  val deserialise: SQSTransform = (message: SQSMessage) => {
    val f = for {
      notification <- fromJson[NotificationMessage](message.getBody)
      work <- fromJson[Work](notification.body)
    } yield work
    (f.toEither, Right(None))
  }
}
