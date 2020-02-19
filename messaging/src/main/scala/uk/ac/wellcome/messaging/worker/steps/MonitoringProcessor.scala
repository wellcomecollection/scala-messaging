package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.models._

import scala.concurrent.{ExecutionContext, Future}

trait MonitoringProcessor[Work, InfraServiceMonitoringContext, InterServiceMonitoringContext] {
  implicit val ec: ExecutionContext

  def recordStart(work: Either[Throwable, Work],
                  context: Either[Throwable, Option[InfraServiceMonitoringContext]]): Future[InterServiceMonitoringContext]

  def recordEnd[Recorded](context: InterServiceMonitoringContext,
                          result: Result[Recorded]): Future[Result[Unit]]
}
