package uk.ac.wellcome.messaging.worker

import io.circe.Encoder
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.worker.models.{Completed, FailedResult, NonDeterministicFailure, Result, Retry, Successful, WorkCompletion}
import uk.ac.wellcome.messaging.worker.steps.{Logger, MessageProcessor, MessageTransform, MonitoringProcessor}

import scala.concurrent.Future
import scala.util.Success

/**
 * A Worker receives a [[Message]] and performs a series of steps. These steps are
 *    - [[MessageTransform]]: deserialises the payload of the message into a [[Work]]
 *    - [[MonitoringProcessor.recordStart]]: starts monitoring
 *    - [[MessageProcessor.process]]: performs an operation on the [[Work]]
      - [[MessageSender.send]]: sends the result of [[MessageProcessor.process]]
 *    - [[Logger.log]]: logs the result of the processing
 *    - [[MonitoringProcessor.recordEnd]]: ends monitoring
 * @tparam Message: the message received by the Worker
 * @tparam Work: the payload in the message
 * @tparam InfraServiceMonitoringContext: the monitoring context to be passed around between different services
 * @tparam InterServiceMonitoringContext: the monitoring context to be passed around within the current service
 * @tparam Value:  the result of the process function
 * @tparam Action: either [[Retry]] or [[Completed]]
 */
trait Worker[Message, Work, InfraServiceMonitoringContext, InterServiceMonitoringContext, Value, Action, Destination, MessageAttributes]
    extends MessageProcessor[Work, Value]
    with MessageTransform[Message, Work, InfraServiceMonitoringContext] with Logger{

  type Processed = Future[(Message, Action)]


  type Completion = WorkCompletion[Message, Value]
  type MessageAction = Message => (Message, Action)

  protected val retryAction: MessageAction
  protected val completedAction: MessageAction

  protected val monitoringProcessor: MonitoringProcessor[Work, InfraServiceMonitoringContext, InterServiceMonitoringContext]
  protected val messageSender: MessageSender[Destination, MessageAttributes]

  final def processMessage(message: Message)(
    implicit encoder: Encoder[Value]): Processed = {
    implicit val e =(monitoringProcessor.ec)
    work(message).map(completion)
  }

  private def work(message: Message)(
    implicit encoder: Encoder[Value]): Future[Completion] = {
    implicit val e =(monitoringProcessor.ec)
    for {
      (workEither, rootContext) <- Future.successful(callTransform(message))
      localContext <- monitoringProcessor.recordStart(workEither, rootContext)
      value <- process(workEither)
      _ <- sendMessage(value, localContext)
      _ <-log(value)
      _ <- monitoringProcessor.recordEnd(localContext, value)
    } yield
      WorkCompletion(
        message,
        value
      )
  }

  private def sendMessage(value: Result[Value], localContext: Either[Throwable, InterServiceMonitoringContext])(
    implicit encoder: Encoder[Value]) = {
    value match {
      case Successful(v) => Future.fromTry(messageSender.sendT(v, None).fold(e => Success(NonDeterministicFailure[Value](e)), v => Success(Successful(v))))
      case failure: FailedResult[_] => Future.successful(failure)
    }
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
