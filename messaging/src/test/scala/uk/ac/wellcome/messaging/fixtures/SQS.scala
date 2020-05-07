package uk.ac.wellcome.messaging.fixtures

import akka.actor.ActorSystem
import grizzled.slf4j.Logging
import io.circe.Encoder
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import software.amazon.awssdk.services.sqs.model._
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
  case class Queue(url: String, arn: String, visibilityTimeout: Int) {
    override def toString = s"SQS.Queue(url = $url, name = $name)"
    def name: String = url.split("/").toList.last
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

  private def setQueueAttribute(
    queueUrl: String,
    attributeName: QueueAttributeName,
    attributeValue: String): SetQueueAttributesResponse =
    sqsClient
      .setQueueAttributes { builder: SetQueueAttributesRequest.Builder =>
        builder
          .queueUrl(queueUrl)
          .attributes(Map(attributeName -> attributeValue).asJava)
      }

  private def getQueueAttribute(queueUrl: String,
                                attributeName: QueueAttributeName): String =
    sqsClient
      .getQueueAttributes { builder: GetQueueAttributesRequest.Builder =>
        builder
          .queueUrl(queueUrl)
          .attributeNames(attributeName)
      }
      .attributes()
      .get(attributeName)

  def getQueueAttribute(queue: Queue,
                        attributeName: QueueAttributeName): String =
    getQueueAttribute(queueUrl = queue.url, attributeName = attributeName)

  def withLocalSqsQueue[R](
    client: SqsClient = sqsClient,
    queueName: String = Random.alphanumeric take 10 mkString,
    visibilityTimeout: Int = 1
  ): Fixture[Queue, R] =
    fixture[Queue, R](
      create = {
        val response = client.createQueue {
          builder: CreateQueueRequest.Builder =>
            builder.queueName(queueName)
        }

        val arn = getQueueAttribute(
          queueUrl = response.queueUrl(),
          attributeName = QueueAttributeName.QUEUE_ARN
        )

        val queue = Queue(
          url = response.queueUrl(),
          arn = arn,
          visibilityTimeout = visibilityTimeout
        )

        setQueueAttribute(
          queueUrl = queue.url,
          attributeName = QueueAttributeName.VISIBILITY_TIMEOUT,
          attributeValue = visibilityTimeout.toString
        )

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

  def withLocalSqsQueuePair[R](visibilityTimeout: Int = 1)(
    testWith: TestWith[QueuePair, R]): R = {
    val queueName = Random.alphanumeric take 10 mkString

    withLocalSqsQueue(sqsClient, queueName = s"$queueName-dlq") { dlq =>
      withLocalSqsQueue(
        sqsClient,
        queueName = queueName,
        visibilityTimeout = visibilityTimeout) { queue =>
        setQueueAttribute(
          queueUrl = queue.url,
          attributeName = QueueAttributeName.REDRIVE_POLICY,
          attributeValue =
            s"""{"maxReceiveCount":"3", "deadLetterTargetArn":"${dlq.arn}"}"""
        )

        testWith(QueuePair(queue, dlq))
      }
    }
  }

  val localStackSqsClient: SqsClient = SQSClientFactory.createSyncClient(
    region = "localhost",
    endpoint = "http://localhost:4576",
    accessKey = sqsAccessKey,
    secretKey = sqsSecretKey
  )

  val localStackSqsAsyncClient: SqsAsyncClient =
    SQSClientFactory.createAsyncClient(
      region = "localhost",
      endpoint = "http://localhost:4576",
      accessKey = sqsAccessKey,
      secretKey = sqsSecretKey
    )

  def withLocalStackSqsQueue[R](testWith: TestWith[Queue, R]): R =
    withLocalSqsQueue(localStackSqsClient) { queue =>
      testWith(queue)
    }

  def withSQSStream[T, R](
    queue: Queue,
    metrics: Metrics[Future, StandardUnit] = new MemoryMetrics[StandardUnit]()
  )(testWith: TestWith[SQSStream[T], R])(
    implicit actorSystem: ActorSystem): R = {
    val sqsConfig = createSQSConfigWith(queue)

    val stream = new SQSStream[T](
      sqsClient = asyncSqsClient,
      sqsConfig = sqsConfig,
      metricsSender = metrics
    )

    testWith(stream)
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
    debug(s"Sending message to ${queue.url}: $body")

    sqsClient.sendMessage { builder: SendMessageRequest.Builder =>
      builder.queueUrl(queue.url).messageBody(body)
    }
  }

  def noMessagesAreWaitingIn(queue: Queue): Assertion = {
    val messagesInFlight = getQueueAttribute(
      queue,
      attributeName =
        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE
    )

    assert(
      messagesInFlight == "0",
      s"Expected messages in flight on ${queue.url} to be 0, actually $messagesInFlight"
    )

    val messagesWaiting = getQueueAttribute(
      queue,
      attributeName = QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES
    )

    assert(
      messagesWaiting == "0",
      s"Expected messages waiting on ${queue.url} to be 0, actually $messagesWaiting"
    )
  }

  def assertQueueEmpty(queue: Queue): Assertion = {
    waitVisibilityTimeoutExpiry(queue)

    val messages = getMessages(queue)

    assert(
      messages.isEmpty,
      s"Expected not to get any messages from ${queue.url}, actually got $messages")

    noMessagesAreWaitingIn(queue)
  }

  def assertQueueHasSize(queue: Queue, size: Int): Assertion = {
    waitVisibilityTimeoutExpiry(queue)

    val messages = getMessages(queue)
    val messagesSize = messages.size

    assert(
      messagesSize == size,
      s"Expected queue ${queue.url} to have size $size, actually had size $messagesSize"
    )
  }

  private def waitVisibilityTimeoutExpiry(queue: Queue): Unit = {
    // Wait slightly longer than the visibility timeout to ensure that messages
    // that fail processing become visible again before asserting.
    val millisecondsToWait = queue.visibilityTimeout * 1500
    Thread.sleep(millisecondsToWait)
  }

  def getMessages(queue: Queue): Seq[Message] =
    sqsClient
      .receiveMessage { builder: ReceiveMessageRequest.Builder =>
        builder.queueUrl(queue.url).maxNumberOfMessages(10)
      }
      .messages()
      .asScala

  def createSQSConfigWith(queue: Queue): SQSConfig =
    SQSConfig(queueUrl = queue.url)
}
