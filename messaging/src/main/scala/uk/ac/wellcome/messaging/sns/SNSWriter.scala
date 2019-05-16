package uk.ac.wellcome.messaging.sns

import grizzled.slf4j.Logging
import io.circe.Encoder

import scala.concurrent.Future

@deprecated("Use BetterSNSMessageSender instead", since = "2019-05-16")
class SNSWriter(snsMessageSender: BetterSNSMessageSender)
    extends Logging {

  def writeMessage(message: String, subject: String): Future[Unit] = Future.fromTry {
    snsMessageSender.send(message, subject)
  }

  def writeMessage[T](message: T, subject: String)(
    implicit encoder: Encoder[T]): Future[Unit] = Future.fromTry {
    snsMessageSender.sendT(message, subject)
  }
}
