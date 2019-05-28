package uk.ac.wellcome.messaging.message

import java.util.concurrent.ConcurrentLinkedQueue

import akka.stream.scaladsl.Flow
import org.mockito.Matchers.{endsWith, eq => equalTo}
import org.mockito.Mockito.{never, times, verify}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import uk.ac.wellcome.storage.memory.MemoryObjectStore
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.{ObjectLocation, ObjectStore}

import scala.concurrent.Future
import scala.util.Random

class MessageStreamTest
    extends FunSpec
    with Matchers
    with Messaging
    with ScalaFutures
    with IntegrationPatience
    with MetricsSenderFixture
    with Eventually {

  def process(list: ConcurrentLinkedQueue[ExampleObject])(o: ExampleObject) = {
    list.add(o)
    Future.successful(())
  }

  describe("small messages (<256KB)") {
    it("reads messages off a queue, processes them and deletes them") {
      implicit val store: ObjectStore[ExampleObject] = new MemoryObjectStore[ExampleObject]()
      withMessageStreamFixtures[ExampleObject, Assertion] {
        case (messageStream, QueuePair(queue, dlq), _) =>
          val messages = createMessages(count = 3)

          messages.foreach { exampleObject =>
            sendInlineNotification(
              queue = queue,
              exampleObject = exampleObject)
          }

          val received = new ConcurrentLinkedQueue[ExampleObject]()

          messageStream.foreach(
            streamName = "test-stream",
            process = process(received))

          eventually {
            received should contain theSameElementsAs messages

            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
      }
    }
  }

  describe("large messages (>256KB)") {
    it("reads messages off a queue, processes them and deletes them") {
      implicit val store: ObjectStore[ExampleObject] = new MemoryObjectStore[ExampleObject]()
      withMessageStreamFixtures[ExampleObject, Assertion] {
        case (messageStream, QueuePair(queue, dlq), _) =>
          val messages = createMessages(count = 3)

          messages.foreach { exampleObject =>
            sendRemoteNotification(queue, exampleObject)
          }

          val received = new ConcurrentLinkedQueue[ExampleObject]()

          messageStream.foreach(
            streamName = "test-stream",
            process = process(received))

          eventually {
            received should contain theSameElementsAs messages

            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
      }
    }
  }

  private def sendInlineNotification(queue: Queue,
                                     exampleObject: ExampleObject): Unit =
    sendNotificationToSQS[MessageNotification](
      queue = queue,
      message = InlineNotification(toJson(exampleObject).get)
    )

  private def sendRemoteNotification[T](queue: Queue, t: T)(implicit store: ObjectStore[T]): Unit = {
    val namespace = Random.alphanumeric take 10 mkString

    val location = store.put(namespace = namespace)(t).right.get

    sendNotificationToSQS[MessageNotification](
      queue = queue,
      message = RemoteNotification(location)
    )
  }

  private def createMessages(count: Int): List[ExampleObject] =
    (1 to count).map { idx =>
      ExampleObject("a" * idx)
    }.toList

  it("increments *_ProcessMessage metric when successful") {
    implicit val store: ObjectStore[ExampleObject] = new MemoryObjectStore[ExampleObject]()
    withMessageStreamFixtures[ExampleObject, Future[Unit]] {
      case (messageStream, QueuePair(queue, _), metricsSender) =>
        val exampleObject = ExampleObject("some value")
        sendInlineNotification(queue = queue, exampleObject = exampleObject)

        val received = new ConcurrentLinkedQueue[ExampleObject]()
        messageStream.foreach(
          streamName = "test-stream",
          process = process(received))

        eventually {
          verify(metricsSender, times(1))
            .incrementCount(equalTo("test-stream_ProcessMessage_success"))
        }
    }
  }

  it("fails gracefully when NotificationMessage cannot be deserialised") {
    implicit val store: ObjectStore[ExampleObject] = new MemoryObjectStore[ExampleObject]()
    withMessageStreamFixtures[ExampleObject, Assertion] {
      case (messageStream, QueuePair(queue, dlq), metricsSender) =>
        sendInvalidJSONto(queue)

        val received = new ConcurrentLinkedQueue[ExampleObject]()

        messageStream.foreach(
          streamName = "test-stream",
          process = process(received))

        eventually {
          verify(metricsSender, never())
            .incrementCount(endsWith("_ProcessMessage_failure"))
          received shouldBe empty

          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }
    }
  }

  it("does not fail gracefully when the object cannot be retrieved") {
    implicit val store: ObjectStore[ExampleObject] = new MemoryObjectStore[ExampleObject]()
    withMessageStreamFixtures[ExampleObject, Assertion] {
      case (messageStream, QueuePair(queue, dlq), metricsSender) =>
        val streamName = "test-stream"

        // Do NOT put an object here
        val objectLocation = ObjectLocation(
          namespace = "bukkit",
          key = "key.json"
        )

        sendNotificationToSQS[MessageNotification](
          queue = queue,
          message = RemoteNotification(objectLocation)
        )

        val received = new ConcurrentLinkedQueue[ExampleObject]()

        messageStream.foreach(
          streamName = streamName,
          process = process(received))

        eventually {
          verify(metricsSender, times(3))
            .incrementCount(metricName = "test-stream_ProcessMessage_failure")

          received shouldBe empty

          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }
    }
  }

  it("continues reading if processing of some messages fails ") {
    implicit val store: ObjectStore[ExampleObject] = new MemoryObjectStore[ExampleObject]()
    withMessageStreamFixtures[ExampleObject, Assertion] {
      case (messageStream, QueuePair(queue, dlq), _) =>
        val exampleObject1 = ExampleObject("some value 1")
        val exampleObject2 = ExampleObject("some value 2")

        sendInvalidJSONto(queue)
        sendInlineNotification(queue = queue, exampleObject = exampleObject1)

        sendInvalidJSONto(queue)
        sendInlineNotification(queue = queue, exampleObject = exampleObject2)

        val received = new ConcurrentLinkedQueue[ExampleObject]()
        messageStream.foreach(
          streamName = "test-stream",
          process = process(received))

        eventually {
          received should contain theSameElementsAs List(
            exampleObject1,
            exampleObject2)

          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 2)
        }
    }
  }

  describe("runStream") {
    it("processes messages off a queue") {
      implicit val store: ObjectStore[ExampleObject] = new MemoryObjectStore[ExampleObject]()
      withMessageStreamFixtures[ExampleObject, Future[Unit]] {
        case (messageStream, QueuePair(queue, dlq), metricsSender) =>
          val exampleObject1 = ExampleObject("some value 1")
          sendInlineNotification(queue = queue, exampleObject = exampleObject1)
          val exampleObject2 = ExampleObject("some value 2")
          sendInlineNotification(queue = queue, exampleObject = exampleObject2)

          val received = new ConcurrentLinkedQueue[ExampleObject]()

          messageStream.runStream(
            "test-stream",
            source =>
              source.via(Flow.fromFunction {
                case (message, t) =>
                  received.add(t)
                  message
              }))

          eventually {
            received should contain theSameElementsAs List(
              exampleObject1,
              exampleObject2)

            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
            verify(metricsSender, times(2))
              .incrementCount("test-stream_ProcessMessage_success")
          }
      }
    }

    it("does not delete failed messages and sends a failure metric") {
      implicit val store: ObjectStore[ExampleObject] = new MemoryObjectStore[ExampleObject]()
      withMessageStreamFixtures[ExampleObject, Future[Unit]] {
        case (messageStream, QueuePair(queue, dlq), metricsSender) =>
          val exampleObject = ExampleObject("some value")
          sendInlineNotification(queue = queue, exampleObject = exampleObject)

          messageStream.runStream(
            "test-stream",
            source =>
              source.via(
                Flow.fromFunction(_ => throw new RuntimeException("BOOOM!"))))

          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)

            verify(metricsSender, times(3))
              .incrementCount("test-stream_ProcessMessage_failure")
          }
      }
    }

    it("continues reading if processing of some messages fails") {
      implicit val store: ObjectStore[ExampleObject] = new MemoryObjectStore[ExampleObject]()
      withMessageStreamFixtures[ExampleObject, Assertion] {
        case (messageStream, QueuePair(queue, dlq), _) =>
          val exampleObject1 = ExampleObject("some value 1")
          val exampleObject2 = ExampleObject("some value 2")

          sendInvalidJSONto(queue)
          sendInlineNotification(queue = queue, exampleObject = exampleObject1)

          sendInvalidJSONto(queue)
          sendInlineNotification(queue = queue, exampleObject = exampleObject2)

          val received = new ConcurrentLinkedQueue[ExampleObject]()
          messageStream.runStream(
            "test-stream",
            source =>
              source.via(Flow.fromFunction {
                case (message, t) =>
                  received.add(t)
                  message
              }))

          eventually {
            received should contain theSameElementsAs List(
              exampleObject1,
              exampleObject2)

            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, size = 2)
          }
      }
    }
  }
}
