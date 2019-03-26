package uk.ac.wellcome.messaging.worker

import java.time.Instant

import uk.ac.wellcome.messaging.worker.models.WorkCompletion
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient
import uk.ac.wellcome.messaging.worker.steps.{
  MessageProcessor,
  MonitoringProcessor
}

import scala.concurrent.{ExecutionContext, Future}

trait Worker[Message, Work, Summary]
    extends MessageProcessor[Message, Work, Summary]
    with MonitoringProcessor {

  protected def work[ProcessMonitoringClient <: MonitoringClient](
    message: Message)(
    implicit monitoringClient: ProcessMonitoringClient,
    ec: ExecutionContext): Future[WorkCompletion[Message, Summary]] = {

    val startTime = Instant.now

    for {
      summary <- process(message)
      monitor <- record(startTime, summary)

    } yield
      WorkCompletion(
        message,
        summary,
        monitor
      )
  }
}
