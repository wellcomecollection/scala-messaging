package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.models._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Records the start and end of an operation to monitor.
  * @tparam Payload: the payload in the message
  * @tparam InfraServiceMonitoringContext: the monitoring context to be passed around between different services
  * @tparam InterServiceMonitoringContext: the monitoring context to be passed around within the current service
  */
trait MonitoringProcessor[
  Payload, InfraServiceMonitoringContext, InterServiceMonitoringContext] {
  implicit val ec: ExecutionContext

  def recordStart(
    work: Either[Throwable, Payload],
    context: Either[Throwable, Option[InfraServiceMonitoringContext]])
    : Future[Either[Throwable, InterServiceMonitoringContext]]

  def recordEnd[Recorded](
    context: Either[Throwable, InterServiceMonitoringContext],
    result: Result[Recorded]): Future[Result[Unit]]
}
