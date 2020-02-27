package uk.ac.wellcome.messaging.worker

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.worker.models.{
  Completed,
  FailedResult,
  NonDeterministicFailure,
  Result,
  Retry,
  Successful,
  WorkCompletion
}
import uk.ac.wellcome.messaging.worker.steps._

import scala.concurrent.Future
import scala.util.Success

/**
  * A Worker receives a [[Message]] and performs a series of steps. These steps are
  *    - [[MessageDeserialiser.apply]]: deserialises the payload of the message into a [[Payload]]
  *    - [[MonitoringRecorder.recordStart]]: starts monitoring
  *    - [[MessageProcessor.process]]: performs an operation on the [[Payload]]
  *    - [[MessageSender.send]]: sends the result of [[MessageProcessor.process]]
  *    - [[Logger.log]]: logs the result of the processing
  *    - [[MonitoringRecorder.recordEnd]]: ends monitoring
  *
  * @tparam Message: the message received by the Worker
  * @tparam Payload: the payload in the message
  * @tparam InfraServiceMonitoringContext: the monitoring context to be passed around between different services
  * @tparam InterServiceMonitoringContext: the monitoring context to be passed around within the current service
  * @tparam Value:  the result of the process function
  * @tparam Action: either [[Retry]] or [[Completed]]
  */
trait Worker[Message,
             Payload,
             InfraServiceMonitoringContext,
             InterServiceMonitoringContext,
             Value,
             Action,
             SerialisedMonitoringContext]
    extends MessageProcessor[Payload, Value]
    with Logger {

  type Processed = Future[(Message, Action)]

  type Completion = WorkCompletion[Message, Value]
  type MessageAction = Message => (Message, Action)

  protected val msgDeserialiser: MessageDeserialiser[
    Message,
    Payload,
    InfraServiceMonitoringContext]

  protected val msgSerialiser: MessageSerialiser[Value,
                                                 InterServiceMonitoringContext,
                                                 SerialisedMonitoringContext]

  protected val retryAction: MessageAction
  protected val completedAction: MessageAction

  protected val monitoringProcessor: MonitoringProcessor[
    Payload,
    InfraServiceMonitoringContext,
    InterServiceMonitoringContext, SerialisedMonitoringContext]
  protected val messageSender: MessageSender[SerialisedMonitoringContext]

  final def processMessage(message: Message): Processed = {
    implicit val e = (monitoringProcessor.ec)
    work(message).map(completion)
  }

  private def work(message: Message): Future[Completion] = {
    implicit val e = (monitoringProcessor.ec)
    for {
      (workEither, rootContext) <- Future.successful(msgDeserialiser(message))
      localContext <- monitoringProcessor.recordStart(workEither, rootContext)
      value <- process(workEither)
      _ <- sendMessage(value, localContext)
      _ <- log(value)
      _ <- monitoringProcessor.recordEnd(localContext, value)
    } yield
      WorkCompletion(
        message,
        value
      )
  }

  private def sendMessage(
    value: Result[Value],
    localContext: Either[Throwable, InterServiceMonitoringContext]) = {
    value match {
      case Successful(v) =>
        Future.fromTry {
          val (body, attributes) = msgSerialiser(v, localContext.right.get)
          messageSender
            .send(body.right.get, Some(attributes.right.get))
            .fold(
              e => Success(NonDeterministicFailure[Value](e)),
              v => Success(Successful(v)))
        }
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
