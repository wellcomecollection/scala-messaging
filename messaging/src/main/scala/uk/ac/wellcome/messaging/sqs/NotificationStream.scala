package uk.ac.wellcome.messaging.sqs

import akka.Done
import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage

import scala.concurrent.{ExecutionContext, Future}

class NotificationStream[T](sqsStream: SQSStream[NotificationMessage])(
  implicit decoder: Decoder[T],
  ec: ExecutionContext) {
  def run(processMessage: T => Future[Unit]): Future[Done] =
    sqsStream.foreach(
      this.getClass.getSimpleName,
      processNotification(processMessage))

  def processNotification(processMessage: T => Future[Unit])(
    notificationMessage: NotificationMessage): Future[Unit] =
    for {
      message <- Future.fromTry(fromJson[T](notificationMessage.body))
      _ <- processMessage(message)
    } yield ()
}
