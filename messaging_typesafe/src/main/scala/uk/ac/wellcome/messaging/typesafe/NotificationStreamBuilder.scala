package uk.ac.wellcome.messaging.typesafe

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config
import io.circe.Decoder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.NotificationStream

import scala.concurrent.ExecutionContext

object NotificationStreamBuilder {
  def buildStream[T](config: Config)(
    implicit decoder: Decoder[T],
    actorSystem: ActorSystem,
    materializer: Materializer,
    ec: ExecutionContext): NotificationStream[T] =
    new NotificationStream[T](
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config)
    )
}
