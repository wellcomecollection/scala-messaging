package uk.ac.wellcome.messaging.worker

import java.time.Instant

import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient
import uk.ac.wellcome.messaging.worker.steps.{
  MessageProcessor,
  MonitoringProcessor
}

import scala.concurrent.{ExecutionContext, Future}

case class WorkCompletion[Message, Summary](
  message: Message,
  summary: Result[Summary],
  recorded: Result[Unit]
)

trait Worker[Message, Work, Summary, Response]
    extends MessageProcessor[Message, Work, Summary]
    with MonitoringProcessor {

  protected def work[ProcessMonitoringClient <: MonitoringClient](
    id: String,
    message: Message
  )(implicit monitoringClient: ProcessMonitoringClient,
    ec: ExecutionContext): Future[WorkCompletion[Message, Summary]] = {
    val startTime = Instant.now

    for {
      summary <- process(id)(message)
      monitor <- record(id)(startTime, summary)

    } yield
      WorkCompletion(
        message,
        summary,
        monitor
      )
  }
}
