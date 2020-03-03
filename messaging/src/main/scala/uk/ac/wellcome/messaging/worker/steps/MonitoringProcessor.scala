package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.models._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Records the start and end of an operation to monitor.
  * @tparam Payload: the payload in the message
  * @tparam MessageMetadata: the monitoring context to be passed around between different services
  * @tparam Trace: the monitoring context to be passed around within the current service
  */
trait MonitoringProcessor[Payload, MessageMetadata, Trace] {
  implicit val ec: ExecutionContext

  def recordStart(deserialised: Either[Throwable, (Payload, MessageMetadata)])
    : Future[Either[Throwable, (Trace, MessageMetadata)]]

  def recordEnd[Recorded](context: Either[Throwable, (Trace, MessageMetadata)],
                          result: Result[Recorded]): Future[Result[Unit]]
}
