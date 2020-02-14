package uk.ac.wellcome.messaging.worker.steps

import scala.util.Try

trait MessageTransform[Message, Work, MonitoringContext] {

  type Transformed = (Either[Throwable,Work], Either[Throwable, Option[MonitoringContext]])

  protected val transform: Message => Transformed

  final def callTransform(message: Message): Transformed = {
    Try(transform(message)).fold(
      e => (Left(e), Left(e)),
      result => result
    )
  }
}
