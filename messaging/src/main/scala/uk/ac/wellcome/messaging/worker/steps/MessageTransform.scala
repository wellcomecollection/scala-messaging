package uk.ac.wellcome.messaging.worker.steps

import scala.util.Try

/**
 * Deserialises a [[Message]] into a [[Work]] and an optional [[InfraServiceMonitoringContext]]
 */
trait MessageTransform[Message, Work, InfraServiceMonitoringContext] {

  type Transformed =
    (Either[Throwable, Work], Either[Throwable, Option[InfraServiceMonitoringContext]])

  protected val transform: Message => Transformed

  final def callTransform(message: Message): Transformed = {
    Try(transform(message)).fold(
      e => (Left(e), Left(e)),
      result => result
    )
  }
}
