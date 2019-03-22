package uk.ac.wellcome.messaging.worker

import java.time.Instant

import uk.ac.wellcome.messaging.worker.monitoring.{MonitoringClient, ProcessMonitor, SummaryRecorder}

import scala.concurrent.{ExecutionContext, Future}

trait PostProcessor[ExternalMessageAction]
  extends ProcessMonitor
    with SummaryRecorder {

  protected def toAction(action: Action): Future[ExternalMessageAction]

  protected def doPostProcess[ProcessMonitoringClient <: MonitoringClient](id: String, startTime: Instant, result: Result[_])(implicit monitoringClient: ProcessMonitoringClient, ec: ExecutionContext) = (
    for {
      _ <- record(result)
      _ <- monitor(result, startTime)
    } yield result) recover {
    case e => PostProcessFailure(id, e)
  }
}
