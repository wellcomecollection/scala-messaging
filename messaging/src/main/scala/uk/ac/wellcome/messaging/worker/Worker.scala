package uk.ac.wellcome.messaging.worker

import io.circe.Encoder
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.worker.models.{Completed, DeterministicFailure, FailedResult, NonDeterministicFailure, Result, Retry, Successful, WorkCompletion}
import uk.ac.wellcome.messaging.worker.steps._

import scala.concurrent.Future
import scala.util.Success

/**
  * A Worker receives a [[Message]] and performs a series of steps. These steps are
  *    - [[MessageDeserialiser.apply]]: deserialises the payload of the message into a [[Payload]]
  *    - [[MonitoringProcessor.recordStart]]: starts monitoring
  *    - [[MessageProcessor.process]]: performs an operation on the [[Payload]]
  *    - [[MessageSender.send]]: sends the result of [[MessageProcessor.process]]
  *    - [[Logger.log]]: logs the result of the processing
  *    - [[MonitoringProcessor.recordEnd]]: ends monitoring
  *
  * @tparam Message: the message received by the Worker
  * @tparam Payload: the payload in the message
  * @tparam Trace: the monitoring context to be passed around within the current service
  * @tparam Value:  the result of the process function
  * @tparam Action: either [[Retry]] or [[Completed]]
  */
trait Worker[Message,
 MessageMetadata,
             Payload,
             Trace,
             Value,
             Action]
    extends MessageProcessor[Payload, Value]
    with Logger {

  type Processed = Future[(Message, Action)]

  type Completion = WorkCompletion[Message, Value]
  type MessageAction = Message => (Message, Action)

  protected val msgDeserialiser: MessageDeserialiser[
    Message, Payload, MessageMetadata]

  protected val retryAction: MessageAction
  protected val completedAction: MessageAction

  protected val monitoringProcessor: MonitoringProcessor[
    Payload,
    MessageMetadata,
    Trace]
  protected val messageSender: MessageSender[MessageMetadata]

  final def processMessage(message: Message)(implicit encoder: Encoder[Value]): Processed = {
    implicit val e = (monitoringProcessor.ec)
    work(message).map(completion)
  }

  private def work(message: Message)(implicit encoder: Encoder[Value]): Future[Completion] = {
    implicit val e = (monitoringProcessor.ec)
    for {
      deserialised <- Future.successful(msgDeserialiser(message))
      trace <- monitoringProcessor.recordStart(deserialised)
      value <- deserialised.fold(e => Future.successful(DeterministicFailure[Value](e)), {case (work, _)  => process(work)})
      _ <- sendMessage(value, trace)
      _ <- log(value)
      _ <- monitoringProcessor.recordEnd(trace, value)
    } yield
      WorkCompletion(
        message,
        value
      )
  }

  private def sendMessage(
                           value: Result[Value],
                           tracingContext: Either[Throwable, (Trace, MessageMetadata)])(implicit encoder: Encoder[Value]) = {
    value match {
      case Successful(v) =>
        Future.fromTry {
          messageSender
            .sendT(v, tracingContext.right.get._2).fold(
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
