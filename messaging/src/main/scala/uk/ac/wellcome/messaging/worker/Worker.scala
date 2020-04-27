package uk.ac.wellcome.messaging.worker

import uk.ac.wellcome.messaging.worker.models.{Completed, Retry, WorkCompletion}
import uk.ac.wellcome.messaging.worker.steps.{
  Logger,
  MessageProcessor,
  MessageTransform,
  MonitoringProcessor
}

import scala.concurrent.Future

/**
  * A Worker receives a `Message` and performs a series of steps. These steps are
  *    - [[uk.ac.wellcome.messaging.worker.steps.MessageTransform]]: deserialises the payload of the message into a `Work`
  *    - [[uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor#recordStart]]: starts monitoring
  *    - [[uk.ac.wellcome.messaging.worker.steps.MessageProcessor#process]]: performs an operation on the `Work`
  *    - [[uk.ac.wellcome.messaging.worker.steps.Logger#log]]: logs the result of the processing
  *    - [[uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor#recordEnd]]: ends monitoring
  * @tparam Message: the message received by the Worker
  * @tparam Work: the payload in the message
  * @tparam InfraServiceMonitoringContext: the monitoring context to be passed around between different services
  * @tparam InterServiceMonitoringContext: the monitoring context to be passed around within the current service
  * @tparam Summary: description of the result of the process function
  * @tparam Action: either [[Retry]] or [[Completed]]
  */
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
