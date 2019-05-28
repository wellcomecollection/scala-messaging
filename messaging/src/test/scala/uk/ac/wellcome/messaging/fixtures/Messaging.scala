package uk.ac.wellcome.messaging.fixtures

import akka.actor.ActorSystem
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.{SubscribeRequest, SubscribeResult, UnsubscribeRequest}
import com.amazonaws.services.sqs.model.SendMessageResult
import io.circe.{Decoder, Encoder}
import org.scalatest.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.{Fixture, TestWith, fixture}
import uk.ac.wellcome.messaging.message._
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import uk.ac.wellcome.storage.ObjectStore

import scala.util.{Random, Success}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.storage.memory.MemoryObjectStore

import scala.concurrent.ExecutionContext.Implicits.global

trait Messaging
    extends Akka
    with MetricsSenderFixture
    with SQS
    with SNS
    with Matchers {

  case class ExampleObject(name: String)

  def withLocalStackSubscription[R](
    queue: Queue,
    topic: Topic): Fixture[SubscribeResult, R] =
    fixture[SubscribeResult, R](
      create = {
        val subRequest = new SubscribeRequest(topic.arn, "sqs", queue.arn)
        info(s"Subscribing queue ${queue.arn} to topic ${topic.arn}")

        localStackSnsClient.subscribe(subRequest)
      },
      destroy = { subscribeResult =>
        val unsubscribeRequest =
          new UnsubscribeRequest(subscribeResult.getSubscriptionArn)
        localStackSnsClient.unsubscribe(unsubscribeRequest)
      }
    )

  def withMessageWriter[T, R](topic: Topic,
                              writerSnsClient: AmazonSNS = snsClient)(
    testWith: TestWith[MessageWriter[T], R])(
    implicit store: ObjectStore[T], encoder: Encoder[T]): R = {
    val messageConfig = MessageWriterConfig(
      namespace = Random.alphanumeric.take(10) mkString,
      snsConfig = createSNSConfigWith(topic)
    )

    val messageWriter = new MessageWriter[T](
      messageConfig = messageConfig,
      snsClient = writerSnsClient
    )

    testWith(messageWriter)
  }

  def withMessageStream[T, R](queue: SQS.Queue, metricsSender: MetricsSender)(
    testWith: TestWith[MessageStream[T], R])(
    implicit
    actorSystem: ActorSystem,
    decoderT: Decoder[T],
    objectStoreT: ObjectStore[T]): R = {
    val stream = new MessageStream[T](
      sqsClient = asyncSqsClient,
      sqsConfig = createSQSConfigWith(queue),
      metricsSender = metricsSender
    )
    testWith(stream)
  }

  def withMessageStreamFixtures[T, R](
    testWith: TestWith[(MessageStream[T], QueuePair, MetricsSender), R]
  )(implicit
    decoderT: Decoder[T],
    objectStore: ObjectStore[T]): R =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueueAndDlq {
        case queuePair @ QueuePair(queue, _) =>
          withMockMetricsSender { metricsSender =>
            withMessageStream[T, R](queue, metricsSender) { stream =>
              testWith((stream, queuePair, metricsSender))
            }
          }
      }
    }

  /** Given a topic ARN which has received notifications containing pointers
    * to objects in S3, return the unpacked objects.
    */
  def getMessages[T](store: MemoryObjectStore[T], topic: Topic)(implicit decoder: Decoder[T]): List[T] =
    listMessagesReceivedFromSNS(topic).map { messageInfo =>
      fromJson[MessageNotification](messageInfo.message) match {
        case Success(RemoteNotification(location)) =>
          store.get(location).right.get
        case Success(InlineNotification(jsonString)) =>
          fromJson[T](jsonString).get
        case _ =>
          throw new RuntimeException(
            s"Unrecognised message: ${messageInfo.message}"
          )
      }
    }.toList

  /** Send a MessageNotification to SQS.
    *
    * As if another application had used a MessageWriter to send the message
    * to an SNS topic, which was forwarded to the queue.  We don't use a
    * MessageWriter instance because that sends to SNS, not SQS.
    *
    * We always send an InlineNotification regardless of size, which makes for
    * slightly easier debugging if queue messages ever fail.
    *
    */
  def sendMessage[T](queue: Queue, obj: T)(
    implicit encoder: Encoder[T]): SendMessageResult =
    sendNotificationToSQS[MessageNotification](
      queue = queue,
      message = InlineNotification(jsonString = toJson(obj).get)
    )
}
