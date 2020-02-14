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
import uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor

import scala.concurrent.Future

class AlpakkaSQSWorker[Work, MonitoringContext, Summary](
  config: AlpakkaSQSWorkerConfig
)(
  val doWork: Work => Future[Result[Summary]]
)(implicit
  val monitoringProcessor: MonitoringProcessor[Work, MonitoringContext],
  val as: ActorSystem,
  val wd: Decoder[Work],
  sc: AmazonSQSAsync,
) extends AkkaWorker[
      SQSMessage,
      Work,
      MonitoringContext,
      Summary,
      MessageAction]
    with SnsSqsTransform[Work, MonitoringContext]
    with Logging {

  type SQSAction = SQSMessage => (SQSMessage, sqs.MessageAction)

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
  def apply[Work, MonitoringContext, Summary](config: AlpakkaSQSWorkerConfig)(
    process: Work => Future[Result[Summary]]
  )(implicit
    mp: MonitoringProcessor[Work, MonitoringContext],
    sc: AmazonSQSAsync,
    as: ActorSystem,
    wd: Decoder[Work]) =
    new AlpakkaSQSWorker[Work, MonitoringContext, Summary](
      config
    )(process)
}
