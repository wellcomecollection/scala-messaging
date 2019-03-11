package uk.ac.wellcome.messaging.fixtures

import io.circe.Decoder
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.NotificationStream

import scala.concurrent.ExecutionContext.Implicits.global

trait NotificationStreamFixture extends Akka with SQS {
  def withNotificationStream[T, R](queue: Queue)(testWith: TestWith[NotificationStream[T], R])(implicit decoder: Decoder[T]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val notificationStream = new NotificationStream[T](sqsStream)
        testWith(notificationStream)
      }
    }
}
