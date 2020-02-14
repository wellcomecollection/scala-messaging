package uk.ac.wellcome.messaging.worker

import uk.ac.wellcome.messaging.worker.models.{Completed, Retry, WorkCompletion}
import uk.ac.wellcome.messaging.worker.steps.{MessageProcessor, MessageTransform, MonitoringProcessor}

import scala.concurrent.{ExecutionContext, Future}

trait Worker[Message, Work, MonitoringContext, Summary, Action]
    extends MessageProcessor[Work, Summary] with MessageTransform[Message, Work, MonitoringContext]{

  type Processed = Future[(Message, Action)]

  implicit val ec: ExecutionContext

  type Completion = WorkCompletion[Message, Summary]
  type MessageAction = Message => (Message, Action)

  protected val retryAction: MessageAction
  protected val completedAction: MessageAction
  val monitoringProcessor: MonitoringProcessor[Work, MonitoringContext]

  final def processMessage(message: Message): Processed =
    work(message).map(completion)

  private def work(message: Message): Future[Completion] = {
    for {
      (workEither, rootContext) <- Future.successful(callTransform(message))
      localContext <- monitoringProcessor.recordStart(workEither, rootContext)
      summary <- process(workEither)
      _ <- monitoringProcessor.recordEnd(workEither, localContext, summary)
    } yield
      WorkCompletion(
        message,
        summary
      )
  }

  private def completion(done: Completion) =
    done match {
      case WorkCompletion(message, response) =>
        response.asInstanceOf[Action] match {
          case _: Retry     => retryAction(message)
          case _: Completed => completedAction(message)
        }
    }

}
