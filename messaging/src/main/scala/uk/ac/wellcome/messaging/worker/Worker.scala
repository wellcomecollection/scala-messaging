package uk.ac.wellcome.messaging.worker

import java.time.Instant

import uk.ac.wellcome.messaging.worker.monitoring.{MonitoringClient, ProcessMonitor, SummaryRecorder}

import scala.concurrent.{ExecutionContext, Future}

trait Worker[
ProcessMonitoringClient <: MonitoringClient,
Work,
Summary,
WorkerMessage,
Action,
MessageProcess <: WorkerProcess[
  Work,
  Summary]
] extends SummaryRecorder
  with ProcessMonitor[ProcessMonitoringClient] {

  protected val process: MessageProcess

  protected def toWork(message: WorkerMessage): Future[Work]
  protected def toAction(result: Result[_]): Future[Action]

  private def doProcess(
                      id: String,
                      message: WorkerMessage
                    )(implicit ec: ExecutionContext
                       ) = {

    val result: Future[Result[_]] = for {
      work <- toWork(message)
      result <- process.run(work)
    } yield result

    val recoveredResult = result.recover {
      case e => DeterministicFailure(id, e, None)
    }

    recoveredResult
  }

  private def doPostProcess(
                           id: String,
                           startTime: Instant,
                           result: Result[_]
                         )(implicit
                           monitoringClient: ProcessMonitoringClient,
                           ec: ExecutionContext
                           ) = {

    val postProcessResult = for {
      _ <- record(result)
      _ <- monitor(result, startTime)
    } yield result

    postProcessResult.recover {
      case e => PostProcessFailure(id, e)
    }
  }

  protected def processMessage(
                                id: String,
                                message: WorkerMessage
                              )(implicit
                                monitoringClient: ProcessMonitoringClient,
                                ec: ExecutionContext
                              ): Future[(WorkerMessage, Action)] = {
    val startTime = Instant.now

    val recoveredPostProcessResult = for {
      processResult <- doProcess(id, message)
      postProcessResult <- doPostProcess(id, startTime, processResult)
    } yield postProcessResult

    for {
      result <- recoveredPostProcessResult
      action <- toAction(result)
    } yield (message, action)
  }
}
