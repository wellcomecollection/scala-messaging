package uk.ac.wellcome.messaging

import io.circe.Encoder
import uk.ac.wellcome.json.JsonUtil.toJson

import scala.util.Try

trait IndividualMessageSender[Destination] {
  def send(message: String)(subject: String, destination: Destination): Try[Unit]

  def sendT[T](t: T)(subject: String, destination: Destination)(implicit encoder: Encoder[T]): Try[Unit] =
    toJson(t).flatMap { send(_)(subject, destination) }
}

trait MessageSender[Destination] {
  protected val underlying: IndividualMessageSender[Destination]

  protected val subject: String
  protected val destination: Destination

  def send(message: String): Try[Unit] =
    underlying.send(message)(subject, destination)

  def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] =
    underlying.sendT(t)(subject, destination)
}
