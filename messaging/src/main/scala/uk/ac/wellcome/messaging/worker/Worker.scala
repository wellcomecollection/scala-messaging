package uk.ac.wellcome.messaging.worker

import java.time.Instant

import uk.ac.wellcome.messaging.worker.models.{Completed, Retry, WorkCompletion}
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient
import uk.ac.wellcome.messaging.worker.steps.{
  MessageProcessor,
  MonitoringProcessor
}

import scala.concurrent.{ExecutionContext, Future}

trait Worker[Message, Work, Summary, Action]
    extends MessageProcessor[Message, Work, Summary]
    with MonitoringProcessor {

  type Processed = Future[(Message, Action)]

  implicit val ec: ExecutionContext
  implicit val mc: MonitoringClient

  type Completion = WorkCompletion[Message, Summary]
  type MessageAction = Message => (Message, Action)

  protected val retryAction: MessageAction
  protected val completedAction: MessageAction

  protected def processMessage(message: Message): Processed =
    work(message).map(completion)

  private def work(message: Message): Future[Completion] = {
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

  private def completion(done: Completion) =
    done match {
      case WorkCompletion(message, response, _) =>
        response.asInstanceOf[Action] match {
          case _: Retry     => retryAction(message)
          case _: Completed => completedAction(message)
        }
    }

}
