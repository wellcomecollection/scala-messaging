package uk.ac.wellcome.messaging.sqsworker.alpakka

import akka.actor.ActorSystem
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.{Done, NotUsed}
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.Message
import grizzled.slf4j.Logging
import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.worker._
import uk.ac.wellcome.messaging.worker.models._
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient

import scala.concurrent.Future

object AlpakkaSQSWorker {
  def apply[Work, Summary](config: AlpakkaSQSWorkerConfig)(
    process: Work => Future[Result[Summary]]
  )(implicit monitoringClient: MonitoringClient,
    sqsClient: AmazonSQSAsync,
    decoder: Decoder[Work],
    actorSystem: ActorSystem) = {

    new AlpakkaSQSWorker[Work, Summary](config)(process)

  }
}

class AlpakkaSQSWorker[Work, Summary](
  config: AlpakkaSQSWorkerConfig
)(
  messageProcess: Work => Future[Result[Summary]]
)(implicit monitoringClient: MonitoringClient,
  sqsClient: AmazonSQSAsync,
  decoder: Decoder[Work],
  actorSystem: ActorSystem)
    extends Worker[Message, Work, Summary]
    with Logging {

  implicit val _ec = actorSystem.dispatcher
  override val namespace: String = config.namespace

  override protected def processMessage(work: Work) =
    messageProcess(work)

  override protected def transform(message: Message): Future[Work] = {

    val maybeWork = for {
      notification <- fromJson[NotificationMessage](message.getBody)
      work <- fromJson[Work](notification.body)
    } yield work

    Future.fromTry(maybeWork)
  }

  private val source =
    SqsSource(config.queueUrl)
  private val sink: Sink[(Message, MessageAction), Future[Done]] =
    SqsAckSink(config.queueUrl)

  private val processedSource: Source[(Message, MessageAction), NotUsed] =
    source
      .mapAsyncUnordered(config.parallelism) { message =>
        work(message)
      }
      .map {
        case WorkCompletion(message, response, _) =>
          response.asInstanceOf[Action] match {
            case _: Retry =>
              (message, MessageAction.changeMessageVisibility(0))
            case _: Completed => (message, MessageAction.delete)
          }
      }

  def start: Future[Unit] = {
    implicit val _ = ActorMaterializer(
      ActorMaterializerSettings(actorSystem)
    )

    processedSource
      .toMat(sink)(Keep.right)
      .run()
      .map(_ => ())
  }
}

case class AlpakkaSQSWorkerConfig(
  namespace: String,
  queueUrl: String,
  parallelism: Int = 1
)
