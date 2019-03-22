package uk.ac.wellcome.messaging.worker

import java.time.Instant

import uk.ac.wellcome.messaging.worker.monitoring.{MonitoringClient, ProcessMonitor, SummaryRecorder}

import scala.concurrent.{ExecutionContext, Future}

trait PostProcessor
  extends ProcessMonitor
    with SummaryRecorder {

  protected def doPostProcess[ProcessMonitoringClient <: MonitoringClient](id: String, startTime: Instant, result: Result[_])(implicit monitoringClient: ProcessMonitoringClient, ec: ExecutionContext): Future[Result[_]] = {

    val postProcessResult = for {
      _ <- record(result)
      _ <- monitor(result, startTime)
    } yield result

    postProcessResult.recover {
      case e => PostProcessFailure(id, e)
    }
  }
}
