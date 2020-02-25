package uk.ac.wellcome.messaging.worker

import uk.ac.wellcome.messaging.worker.models.{Completed, Retry, WorkCompletion}
import uk.ac.wellcome.messaging.worker.steps.{
  Logger,
  MessageProcessor,
  MessageTransform,
  MonitoringProcessor
}

import scala.concurrent.Future

trait Worker[Message,
             Work,
             InfraServiceMonitoringContext,
             InterServiceMonitoringContext,
             Summary,
             Action]
    extends MessageProcessor[Work, Summary]
    with MessageTransform[Message, Work, InfraServiceMonitoringContext]
    with Logger {

  type Processed = Future[(Message, Action)]

  type Completion = WorkCompletion[Message, Summary]
  type MessageAction = Message => (Message, Action)

  protected val retryAction: MessageAction
  protected val completedAction: MessageAction

  protected val monitoringProcessor: MonitoringProcessor[
    Work,
    InfraServiceMonitoringContext,
    InterServiceMonitoringContext]

  final def processMessage(message: Message): Processed = {
    implicit val e = (monitoringProcessor.ec)
    work(message).map(completion)
  }

  private def work(message: Message): Future[Completion] = {
    implicit val e = (monitoringProcessor.ec)
    for {
      (workEither, rootContext) <- Future.successful(callTransform(message))
      localContext <- monitoringProcessor.recordStart(workEither, rootContext)
      summary <- process(workEither)
      _ <- log(summary)
      _ <- monitoringProcessor.recordEnd(localContext, summary)
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
