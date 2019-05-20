package uk.ac.wellcome.messaging.sns

import com.amazonaws.services.sns.AmazonSNS
import grizzled.slf4j.Logging
import io.circe.Encoder

import scala.concurrent.Future

class SNSMessageWriter(snsClient: AmazonSNS)
    extends Logging {

  private val underlying = new SNSIndividualMessageSender(snsClient)

  def writeMessage(message: String,
                   subject: String,
                   snsConfig: SNSConfig): Future[Unit] =
    Future.fromTry {
      underlying.send(message)(subject, snsConfig)
    }

  def writeMessage[T](message: T, subject: String, snsConfig: SNSConfig)(
    implicit encoder: Encoder[T]): Future[Unit] =
    Future.fromTry {
      underlying.sendT[T](message)(subject, snsConfig)
    }
}
