package uk.ac.wellcome.messaging.worker

import java.time.Instant

import uk.ac.wellcome.messaging.worker.models.{
  IdentifiedMessage,
  WorkCompletion
}
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient
import uk.ac.wellcome.messaging.worker.steps.{
  MessageProcessor,
  MonitoringProcessor
}

import scala.concurrent.{ExecutionContext, Future}

trait Worker[Message, Work, Summary, Response]
    extends MessageProcessor[Message, Work, Summary]
    with MonitoringProcessor {

  protected def work[ProcessMonitoringClient <: MonitoringClient](
    in: Message
  )(implicit monitoringClient: ProcessMonitoringClient,
    ec: ExecutionContext,
    messageToIdentified: Message => IdentifiedMessage[Message])
    : Future[WorkCompletion[Message, Summary]] = {

    val startTime = Instant.now

    val identifiedMessage = messageToIdentified(in)

    val id = identifiedMessage.id
    val message = identifiedMessage.message

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
