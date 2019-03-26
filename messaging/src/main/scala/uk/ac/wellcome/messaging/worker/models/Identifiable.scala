package uk.ac.wellcome.messaging.worker.models

sealed trait Identifiable {
  val id: String
}

case class IdentifiedMessage[T](id: String, message: T) extends Identifiable
