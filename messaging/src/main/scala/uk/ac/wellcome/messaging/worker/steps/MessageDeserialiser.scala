package uk.ac.wellcome.messaging.worker.steps

import scala.util.Try

/**
  * Deserialises a [[Message]] into a [[Payload]] and an optional [[InfraServiceMonitoringContext]]
  */
trait MessageDeserialiser[Message, Payload, MessageMetadata] {

  type Deserialised = Either[Throwable, (Payload, MessageMetadata)]

  protected def deserialise(msg: Message): Deserialised

  final def apply(message: Message): Deserialised = {
    Try(deserialise(message)).fold(
      e => Left(e),
      result => result
    )
  }
}
