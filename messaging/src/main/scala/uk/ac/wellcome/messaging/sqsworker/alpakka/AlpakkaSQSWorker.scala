package uk.ac.wellcome.messaging.sqsworker.alpakka

import akka.actor.ActorSystem
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.{Done, NotUsed}
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.worker._
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient

import scala.concurrent.Future

class AlpakkaSQSWorker[
ProcessMonitoringClient <: MonitoringClient,
Work,
Summary,
MessageProcess <: WorkerProcess[
  Work,
  Summary]
](
   config: AlpakkaSQSWorkerConfig
 )(
   messageProcess: MessageProcess
 )(implicit
   monitoringClient: ProcessMonitoringClient,
   sqsClient: AmazonSQSAsync,
   decoder: Decoder[Work],
   actorSytem: ActorSystem
 ) extends Worker[
  ProcessMonitoringClient,
  Work,
  Summary,
  SQSMessage,
  MessageAction,
  MessageProcess] {

  implicit val _ec = actorSytem.dispatcher

  override val namespace: String = config.namespace

  override protected val process: MessageProcess = messageProcess

  override protected def toWork(message: SQSMessage): Future[Work] = {
    val maybeWork = for {
      notification <- fromJson[NotificationMessage](message.getBody)
      work <- fromJson[Work](notification.body)
    } yield work

    Future.fromTry(maybeWork)
  }

  override protected def toMessageAction(result: Result[_]): Future[MessageAction] = Future {
    result match {
      case _: Retry => MessageAction.changeMessageVisibility(0)
      case _: Completed => MessageAction.delete
    }
  }

  private val source = SqsSource(config.queueUrl)
  private val sink: Sink[(SQSMessage, MessageAction), Future[Done]]
  = SqsAckSink(config.queueUrl)

  private val processedSource: Source[(SQSMessage, MessageAction), NotUsed] = source
    .mapAsyncUnordered(config.parallelism) {
      message: SQSMessage => processMessage(message.getMessageId, message)
    }

  def start: Future[Unit] = {
    implicit val _ = ActorMaterializer(
      ActorMaterializerSettings(actorSytem)
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