package uk.ac.wellcome.messaging

import io.circe.Encoder
import uk.ac.wellcome.json.JsonUtil.toJson

import scala.util.Try

trait BetterMessageSender[DestinationConfig] {
  val defaultDestination: DestinationConfig

  def send(
    message: String,
    subject: String,
    destination: DestinationConfig = defaultDestination): Try[Unit]

  def sendT[T](
    message: T,
    subject: String,
    destination: DestinationConfig = defaultDestination)(
    implicit encoder: Encoder[T]): Try[Unit] =
    toJson(message).flatMap {
      send(_, subject, destination)
    }
}
