package uk.ac.wellcome.messaging.worker

import java.time.Instant

import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient
import uk.ac.wellcome.messaging.worker.steps.{MessageProcessor, MonitoringProcessor, ResultProcessor}

import scala.concurrent.{ExecutionContext, Future}

case class WorkCompletion[Message, Response, Recorded](
  message: Message,
  response: Result[Response],
  recorded: Result[Recorded]
)

trait Worker[Message, Work, Summary, Response]
  extends MessageProcessor[Message, Work, Summary]
    with MonitoringProcessor
    with ResultProcessor[Summary, Response] {

  protected def work[ProcessMonitoringClient <: MonitoringClient](
                                id: String,
                                message: Message
                              )(implicit
                                monitoringClient: ProcessMonitoringClient,
                                ec: ExecutionContext
                              ): Future[WorkCompletion[Message, Response, Summary]] = {
    val startTime = Instant.now

    for {
      summary  <- process(id)(message)
      response <- result(id)(summary)

      monitor <- record(id)(startTime, summary)

    } yield WorkCompletion(message, response, monitor)
  }
}
