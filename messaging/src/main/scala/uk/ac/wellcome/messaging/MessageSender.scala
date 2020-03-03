package uk.ac.wellcome.messaging

import io.circe.Encoder
import uk.ac.wellcome.json.JsonUtil.toJson

import scala.util.Try

trait IndividualMessageSender[Destination, Metadata] {
  def send(body: String, attributes: Metadata)(
    subject: String,
    destination: Destination): Try[Unit]

  def sendT[T](t: T, attributes: Metadata)(
    subject: String,
    destination: Destination)(implicit encoder: Encoder[T]): Try[Unit] =
    toJson(t).flatMap { send(_, attributes)(subject, destination) }
}

trait MessageSender[Metadata] {

  type Destination

  protected val underlying: IndividualMessageSender[Destination, Metadata]

  val subject: String
  val destination: Destination

  def send(body: String, metadata: Metadata): Try[Unit] =
    underlying.send(body, metadata)(subject, destination)

  def sendT[T](t: T, attributes: Metadata)(
    implicit encoder: Encoder[T]): Try[Unit] =
    underlying.sendT[T](t, attributes)(subject, destination)
}
