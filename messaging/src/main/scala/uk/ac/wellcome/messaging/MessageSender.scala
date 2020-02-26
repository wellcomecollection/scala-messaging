package uk.ac.wellcome.messaging

import io.circe.Encoder
import uk.ac.wellcome.json.JsonUtil.toJson

import scala.util.Try

trait IndividualMessageSender[Destination, MessageAttributes] {
  def send(body: String, attributes: Option[MessageAttributes])(
    subject: String,
    destination: Destination): Try[Unit]

  def sendT[T](t: T, attributes: Option[MessageAttributes])(
    subject: String,
    destination: Destination)(implicit encoder: Encoder[T]): Try[Unit] =
    toJson(t).flatMap { send(_, attributes)(subject, destination) }
}

trait MessageSender[Destination, MessageAttributes] {
  protected val underlying: IndividualMessageSender[Destination,
                                                    MessageAttributes]

  val subject: String
  val destination: Destination

  def send(body: String, attributes: Option[MessageAttributes]): Try[Unit] =
    underlying.send(body, attributes)(subject, destination)

  def sendT[T](t: T, attributes: Option[MessageAttributes])(
    implicit encoder: Encoder[T]): Try[Unit] =
    underlying.sendT[T](t, attributes)(subject, destination)
}
