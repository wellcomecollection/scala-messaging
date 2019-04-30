package uk.ac.wellcome.messaging.worker

import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage

import scala.concurrent.Future

sealed trait MessageTransform[Message, Work] {
  val transform: Message => Future[Work]
}

trait SnsSqsTransform[Work]
  extends MessageTransform[SQSMessage, Work] {

  type SQSTransform = SQSMessage => Future[Work]

  implicit val nd = implicitly[Decoder[NotificationMessage]]
  implicit val wd: Decoder[Work]

  val transform: SQSTransform = (message: SQSMessage) =>
    Future.fromTry(
      for {
        notification <-
          fromJson[NotificationMessage](message.getBody)
        work <-
          fromJson[Work](notification.body)
      } yield work
    )
}
