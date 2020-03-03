package uk.ac.wellcome.messaging.sqsworker.alpakka

import akka.actor.ActorSystem
import akka.stream.alpakka.sqs
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import grizzled.slf4j.Logging
import io.circe.Decoder
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.worker._
import uk.ac.wellcome.messaging.worker.models._
import uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor

import scala.concurrent.{ExecutionContext, Future}

/***
  * Implementation of [[AkkaWorker]] that uses SQS as source and sink.
  * It receives messages from SQS and deletes messages from SQS on successful completion
  */
class AlpakkaSQSWorker[Payload, Trace, Value](
  config: AlpakkaSQSWorkerConfig,
  val monitoringProcessorBuilder: (
    ExecutionContext) => MonitoringProcessor[Payload,
                                             Map[String, String],
                                             Trace],
  val messageSender: MessageSender[Map[String, String]]
)(
  val doWork: Payload => Future[Result[Value]]
)(implicit
  val as: ActorSystem,
  val wd: Decoder[Payload],
  sc: AmazonSQSAsync,
) extends AkkaWorker[
      SQSMessage,
      Map[String, String],
      Payload,
      Trace,
      Value,
      MessageAction]
    with Logging {

  type SQSAction = SQSMessage => (SQSMessage, sqs.MessageAction)

  protected val msgDeserialiser = new SnsSqsDeserialiser[Payload]

  val parallelism: Int = config.sqsConfig.parallelism
  val source = SqsSource(config.sqsConfig.queueUrl)
  val sink = SqsAckSink(config.sqsConfig.queueUrl)

  private val makeVisibleAction = MessageAction
    .changeMessageVisibility(visibilityTimeout = 0)

  val retryAction: SQSAction = (message: SQSMessage) =>
    (message, makeVisibleAction)

  val completedAction: SQSAction = (message: SQSMessage) =>
    (message, MessageAction.delete)
}

object AlpakkaSQSWorker {
  def apply[Payload, Trace, Value](
    config: AlpakkaSQSWorkerConfig,
    monitoringProcessorBuilder: (
      ExecutionContext) => MonitoringProcessor[Payload,
                                               Map[String, String],
                                               Trace],
    messageSender: MessageSender[Map[String, String]])(
    process: Payload => Future[Result[Value]]
  )(implicit
    sc: AmazonSQSAsync,
    as: ActorSystem,
    wd: Decoder[Payload]) =
    new AlpakkaSQSWorker[Payload, Trace, Value](
      config,
      monitoringProcessorBuilder,
      messageSender
    )(process)
}
