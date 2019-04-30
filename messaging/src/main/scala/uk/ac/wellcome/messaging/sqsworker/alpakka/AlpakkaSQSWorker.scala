package uk.ac.wellcome.messaging.sqsworker.alpakka

import akka.actor.ActorSystem
import akka.stream.alpakka.sqs
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import grizzled.slf4j.Logging
import io.circe.Decoder
import uk.ac.wellcome.messaging.worker._
import uk.ac.wellcome.messaging.worker.models._
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient

import scala.concurrent.Future

class AlpakkaSQSWorker[Work, Summary,
  MonitoringClientImpl <: MonitoringClient](
  config: AlpakkaSQSWorkerConfig
)(
  val doWork: Work => Future[Result[Summary]]
)(implicit
    val mc: MonitoringClientImpl,
    val as: ActorSystem,
    val wd: Decoder[Work],
    sc: AmazonSQSAsync,
) extends AkkaWorker[SQSMessage, Work, Summary, MessageAction]
    with SnsSqsTransform[Work]
    with Logging {

  type SQSAction = SQSMessage => (SQSMessage, sqs.MessageAction)

  val parallelism: Int = config.parallelism
  val source = SqsSource(config.queueUrl)
  val sink = SqsAckSink(config.queueUrl)

  private val makeVisibleAction = MessageAction
    .changeMessageVisibility(visibilityTimeout = 0)

  val retryAction: SQSAction = (message: SQSMessage) =>
    (message, makeVisibleAction)

  val completedAction: SQSAction = (message: SQSMessage) =>
    (message, MessageAction.delete)

  override val namespace: String = config.namespace
}

object AlpakkaSQSWorker {
  def apply[Work, Summary](config: AlpakkaSQSWorkerConfig)(
    process: Work => Future[Result[Summary]]
  )(implicit
      mc: MonitoringClient,
      sc: AmazonSQSAsync,
      as: ActorSystem,
      wd: Decoder[Work]
  ) = new AlpakkaSQSWorker[Work, Summary, MonitoringClient](
    config
  )(process)
}
