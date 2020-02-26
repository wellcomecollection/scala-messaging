package uk.ac.wellcome.messaging.worker.steps

import scala.util.Try

/**
  * Deserialises a [[Message]] into a [[Payload]] and an optional [[InfraServiceMonitoringContext]]
  */
trait MessageDeserialiser[Message, Payload, InfraServiceMonitoringContext] {

  type Transformed =
    (Either[Throwable, Payload],
     Either[Throwable, Option[InfraServiceMonitoringContext]])

  protected val deserialise: Message => Transformed

  final def callDeserialise(message: Message): Transformed = {
    Try(deserialise(message)).fold(
      e => (Left(e), Left(e)),
      result => result
    )
  }
}
