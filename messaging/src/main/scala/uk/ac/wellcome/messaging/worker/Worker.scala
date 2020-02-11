package uk.ac.wellcome.messaging.worker

import uk.ac.wellcome.messaging.worker.models.{Completed, Retry, WorkCompletion}
import uk.ac.wellcome.messaging.worker.steps.{MessageProcessor, MonitoringProcessor}

import scala.concurrent.{ExecutionContext, Future}

trait Worker[Message, Work, MonitoringContext, Summary, Action]
    extends MessageProcessor[Message, Work, Summary] {

  type Processed = Future[(Message, Action)]

  implicit val ec: ExecutionContext

  type Completion = WorkCompletion[Message, Summary]
  type MessageAction = Message => (Message, Action)

  protected val retryAction: MessageAction
  protected val completedAction: MessageAction

  final protected def processMessage(message: Message)(implicit monitoringProcessor: MonitoringProcessor[Message, MonitoringContext]): Processed =
    work(message).map(completion)

  private def work(message: Message)(implicit monitoringProcessor: MonitoringProcessor[Message, MonitoringContext]): Future[Completion] = {
    for {
      context <- monitoringProcessor.recordStart(message)
      summary <- process(message)
      monitor <- monitoringProcessor.recordEnd(message, context, summary)
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
