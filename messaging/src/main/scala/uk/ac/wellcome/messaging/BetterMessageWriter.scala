package uk.ac.wellcome.messaging

import io.circe.Encoder
import uk.ac.wellcome.json.JsonUtil.toJson

import scala.util.Try

trait BetterMessageWriter[DestinationConfig] {
  val defaultDestination: DestinationConfig

  def writeMessage(
    message: String,
    subject: String,
    destination: DestinationConfig = defaultDestination): Try[Unit]

  def writeT[T](
    message: T,
    subject: String,
    destination: DestinationConfig = defaultDestination)(
    implicit encoder: Encoder[T]): Try[Unit] =
    toJson(message).flatMap {
      writeMessage(_, subject, destination)
    }
}
