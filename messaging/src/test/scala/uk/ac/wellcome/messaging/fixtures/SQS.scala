package uk.ac.wellcome.messaging.fixtures

import akka.actor.ActorSystem
import grizzled.slf4j.Logging
import io.circe.Encoder
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, DeleteQueueRequest, GetQueueAttributesRequest, PurgeQueueRequest, QueueAttributeName, ReceiveMessageRequest, SendMessageRequest, SendMessageResponse, SetQueueAttributesRequest}
import software.amazon.awssdk.services.sqs.{SqsAsyncClient, SqsClient}
import uk.ac.wellcome.fixtures._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs._
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.monitoring.memory.MemoryMetrics

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Random

object SQS {
  case class Queue(url: String, arn: String) {
    override def toString = s"SQS.Queue(url = $url, name = $name)"
    def name = url.split("/").toList.last
  }
  case class QueuePair(queue: Queue, dlq: Queue)
}

trait SQS extends Matchers with Logging {

  import SQS._

  private val sqsInternalEndpointUrl = "http://sqs:9324"
  private val sqsEndpointUrl = "http://localhost:9324"

  private val regionName = "localhost"

  private val sqsAccessKey = "access"
  private val sqsSecretKey = "secret"

  def endpoint(queue: Queue) =
    s"aws-sqs://${queue.name}?amazonSQSEndpoint=$sqsInternalEndpointUrl&accessKey=&secretKey="

  def localStackEndpoint(queue: Queue) =
    s"sqs://${queue.name}"

  implicit val sqsClient: SqsClient = SQSClientFactory.createSyncClient(
    region = regionName,
    endpoint = sqsEndpointUrl,
    accessKey = sqsAccessKey,
    secretKey = sqsSecretKey
  )

  implicit val asyncSqsClient: SqsAsyncClient =
    SQSClientFactory.createAsyncClient(
      region = regionName,
      endpoint = sqsEndpointUrl,
      accessKey = sqsAccessKey,
      secretKey = sqsSecretKey
    )

  private def withLocalSqsQueue[R](client: SqsClient): Fixture[Queue, R] =
    fixture[Queue, R](
      create = {
        val queueName: String = Random.alphanumeric take 10 mkString
        val response = client.createQueue {
          builder: CreateQueueRequest.Builder =>
            builder.queueName(queueName)
        }
        val arn = client
          .getQueueAttributes { builder: GetQueueAttributesRequest.Builder =>
            builder
              .queueUrl(response.queueUrl())
              .attributeNames(QueueAttributeName.QUEUE_ARN)
          }
          .attributes()
          .get(QueueAttributeName.QUEUE_ARN)
        val queue = Queue(response.queueUrl(), arn)
        client
          .setQueueAttributes { builder: SetQueueAttributesRequest.Builder =>
            builder
              .queueUrl(queue.url)
              .attributes(
                Map(QueueAttributeName.VISIBILITY_TIMEOUT -> "1").asJava)
          }
        queue
      },
      destroy = { queue =>
        client.purgeQueue { builder: PurgeQueueRequest.Builder =>
          builder.queueUrl(queue.url)
        }
        client.deleteQueue { builder: DeleteQueueRequest.Builder =>
          builder.queueUrl(queue.url)
        }
      }
    )

  def withLocalSqsQueue[R](testWith: TestWith[Queue, R]): R =
    withLocalSqsQueue(sqsClient) { queue =>
      testWith(queue)
    }

  def withLocalSqsQueueAndDlq[R](testWith: TestWith[QueuePair, R]): R =
    withLocalSqsQueueAndDlqAndTimeout(visibilityTimeout = 1)(testWith)

  def withLocalSqsQueueAndDlqAndTimeout[R](visibilityTimeout: Int)(
    testWith: TestWith[QueuePair, R]): R =
    withLocalSqsQueue { dlq =>
      val queueName: String = Random.alphanumeric take 10 mkString
      val response = sqsClient.createQueue {
        builder: CreateQueueRequest.Builder =>
          builder
            .queueName(queueName)
            .attributes(Map(
              QueueAttributeName.REDRIVE_POLICY -> s"""{"maxReceiveCount":"3", "deadLetterTargetArn":"${dlq.arn}"}""",
              QueueAttributeName.VISIBILITY_TIMEOUT -> s"$visibilityTimeout"
            ).asJava)
      }
      val arn = sqsClient
        .getQueueAttributes { builder: GetQueueAttributesRequest.Builder =>
          builder
            .queueUrl(response.queueUrl())
            .attributeNames(QueueAttributeName.QUEUE_ARN)
        }
        .attributes()
        .get(QueueAttributeName.QUEUE_ARN)
      val queue = Queue(response.queueUrl(), arn)
      testWith(QueuePair(queue, dlq))
    }

  val localStackSqsClient: SqsClient = SQSClientFactory.createSyncClient(
    region = "localhost",
    endpoint = "http://localhost:4576",
    accessKey = sqsAccessKey,
    secretKey = sqsSecretKey
  )

  def withLocalStackSqsQueue[R](testWith: TestWith[Queue, R]): R =
    withLocalSqsQueue(localStackSqsClient) { queue =>
      testWith(queue)
    }

  def withSQSStream[T, R](queue: Queue, metrics: Metrics[Future, StandardUnit])(
    testWith: TestWith[SQSStream[T], R])(
    implicit actorSystem: ActorSystem): R = {
    val sqsConfig = createSQSConfigWith(queue)

    val stream = new SQSStream[T](
      sqsClient = asyncSqsClient,
      sqsConfig = sqsConfig,
      metricsSender = metrics
    )

    testWith(stream)
  }

  def withSQSStream[T, R](queue: Queue)(testWith: TestWith[SQSStream[T], R])(
    implicit actorSystem: ActorSystem): R = {
    val metrics = new MemoryMetrics[StandardUnit]()
    withSQSStream[T, R](queue, metrics) { sqsStream =>
      testWith(sqsStream)
    }
  }

  def createNotificationMessageWith(body: String): NotificationMessage =
    NotificationMessage(body = body)

  def createNotificationMessageWith[T](message: T)(
    implicit encoder: Encoder[T]): NotificationMessage =
    createNotificationMessageWith(body = toJson(message).get)

  def sendNotificationToSQS(queue: Queue, body: String): SendMessageResponse = {
    val message = createNotificationMessageWith(body = body)

    sendSqsMessage(queue = queue, obj = message)
  }

  def sendNotificationToSQS[T](queue: Queue, message: T)(
    implicit encoder: Encoder[T]): SendMessageResponse =
    sendNotificationToSQS(queue = queue, body = toJson[T](message).get)

  def sendSqsMessage[T](queue: Queue, obj: T)(
    implicit encoder: Encoder[T]): SendMessageResponse =
    sendMessageToSqsClient(queue = queue, body = toJson[T](obj).get)

  def sendInvalidJSONto(queue: Queue): SendMessageResponse =
    sendMessageToSqsClient(
      queue = queue,
      body = Random.alphanumeric take 50 mkString)

  private def sendMessageToSqsClient(queue: Queue,
                                     body: String): SendMessageResponse = {
    debug(s"Sending message to ${queue.url}: ${body}")

    sqsClient.sendMessage { builder: SendMessageRequest.Builder =>
      builder.queueUrl(queue.url).messageBody(body)
    }
  }

  def noMessagesAreWaitingIn(queue: Queue) = {
    // No messages in flight
    sqsClient
      .getQueueAttributes { builder: GetQueueAttributesRequest.Builder =>
        builder
          .queueUrl(queue.url)
          .attributeNames(List(
            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE).asJava)
      }
      .attributes()
      .get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE) shouldBe "0"

    // No messages awaiting processing
    sqsClient
      .getQueueAttributes { builder: GetQueueAttributesRequest.Builder =>
        builder
          .queueUrl(queue.url)
          .attributeNames(
            List(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES).asJava)
      }
      .attributes()
      .get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES) shouldBe "0"
  }

  def assertQueueEmpty(queue: Queue) = {
    waitVisibilityTimeoutExipiry()

    val messages = getMessages(queue)

    messages shouldBe empty
    noMessagesAreWaitingIn(queue)
  }

  def assertQueueNotEmpty(queue: Queue) = {
    waitVisibilityTimeoutExipiry()

    val messages = getMessages(queue)

    messages should not be empty
  }

  def assertQueuePairSizes(queue: QueuePair, qSize: Int, dlqSize: Int) = {
    waitVisibilityTimeoutExipiry()

    val messagesDlq = getMessages(queue.dlq)
    val messagesDlqSize = messagesDlq.size

    val messagesQ = getMessages(queue.queue)
    val messagesQSize = messagesQ.size

    debug(
      s"\ndlq: ${queue.dlq.url}, ${messagesDlqSize}\nqueue: ${queue.queue.url}, ${messagesQSize}")

    debug(s"$messagesDlq")

    messagesQSize shouldBe qSize
    messagesDlqSize shouldBe dlqSize
  }

  def assertQueueHasSize(queue: Queue, size: Int) = {
    waitVisibilityTimeoutExipiry()

    val messages = getMessages(queue)
    val messagesSize = messages.size

    messagesSize shouldBe size
  }

  def waitVisibilityTimeoutExipiry() = {
    // The visibility timeout is set to 1 second for test queues.
    // Wait for slightly longer than that to make sure that messages
    // that fail processing become visible again before asserting.
    Thread.sleep(1500)
  }

  def getMessages(queue: Queue) = {
    sqsClient
      .receiveMessage { builder: ReceiveMessageRequest.Builder =>
        builder.queueUrl(queue.url).maxNumberOfMessages(10)
      }
      .messages()
      .asScala
  }

  def createSQSConfigWith(queue: Queue): SQSConfig =
    SQSConfig(queueUrl = queue.url)
}
