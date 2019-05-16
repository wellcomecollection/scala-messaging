package uk.ac.wellcome.messaging

import grizzled.slf4j.Logging
import io.circe.Encoder
import uk.ac.wellcome.json.JsonUtil.toJson

import scala.util.{Failure, Success, Try}

trait BetterMessageSender[DestinationConfig] extends Logging {
  val defaultDestination: DestinationConfig

  def sendIndividualMessage(
    message: String,
    subject: String,
    destination: DestinationConfig
  ): Try[Unit]

  def send(
    message: String,
    subject: String,
    destination: DestinationConfig = defaultDestination): Try[Unit] = {
    debug(s"Sending message <<$message>> to $destination")
    sendIndividualMessage(message, subject, destination) match {
      case success @ Success(_) =>
        debug(s"Successfully sent <<$message>> to $destination")
        success
      case failure @ Failure(err) =>
        error(s"Failed to send <<$message>> to $destination", err)
        failure
    }
  }

  def sendT[T](
    message: T,
    subject: String,
    destination: DestinationConfig = defaultDestination)(
    implicit encoder: Encoder[T]): Try[Unit] =
    toJson(message).flatMap {
      send(_, subject, destination)
    }
}
