package uk.ac.wellcome.messaging.worker

import java.time.Instant

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
  protected def toMessageAction(result: Result[_]): Future[Action]
  protected def processMessage(
                                id: String,
                                message: WorkerMessage
                              )(implicit
                                monitoringClient: ProcessMonitoringClient,
                                ec: ExecutionContext

  ): Future[(WorkerMessage, Action)] = {
    val startTime = Instant.now

    val result = for {
      work <- toWork(message)
      result <- process.run(work)
    } yield result

    val recoveredResult = result.recover {
      case e => DeterministicFailure(id, e)
    }

    val postProcessResult = for {
      result <- recoveredResult

      _ <- record(result)
      _ <- monitor(result, startTime)
    } yield result

    val recoveredPostProcessResult = postProcessResult.recover {
      case e => PostProcessFailure(id, e)
    }

    for {
      result <- recoveredPostProcessResult
      action <- toMessageAction(result)
    } yield (message, action)
  }
}

trait WorkerProcess[Work, Summary] {
  def run(in: Work)(implicit ec: ExecutionContext): Future[Result[Summary]]
}