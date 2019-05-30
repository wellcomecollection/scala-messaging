package uk.ac.wellcome.messaging.sqs

import java.util.concurrent.ConcurrentLinkedQueue

import akka.stream.scaladsl.Flow
import org.mockito.Mockito.{atLeastOnce, never, times, verify}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture

import scala.concurrent.Future

class SQSStreamTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with MetricsSenderFixture
    with Eventually
    with SQS {

  case class NamedObject(name: String)
  
  def process(list: ConcurrentLinkedQueue[NamedObject])(o: NamedObject): Future[Unit] = {
    list.add(o)
    Future.successful(())
  }

  it("reads messages off a queue, processes them and deletes them") {
    withSQSStreamFixtures {
      case (messageStream, QueuePair(queue, dlq), _) =>
        sendNamedObjects(queue = queue, count = 3)

        val received = new ConcurrentLinkedQueue[NamedObject]()

        messageStream.foreach(
          streamName = "test-stream",
          process = process(received))

        eventually {
          received should contain theSameElementsAs createNamedObjects(
            count = 3)

          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }

  }

  it("increments *_ProcessMessage metric when successful") {
    withSQSStreamFixtures {
      case (messageStream, QueuePair(queue, _), metricsSender) =>
        sendNamedObjects(queue = queue)

        val received = new ConcurrentLinkedQueue[NamedObject]()
        messageStream.foreach(
          streamName = "test-stream",
          process = process(received))

        eventually {
          verify(metricsSender, atLeastOnce)
            .incrementCount("test-stream_ProcessMessage_success")
        }
    }
  }

  it("fails gracefully when the object cannot be deserialised") {
    withSQSStreamFixtures {
      case (messageStream, QueuePair(queue, dlq), metricsSender) =>
        sendInvalidJSONto(queue)

        val received = new ConcurrentLinkedQueue[NamedObject]()

        messageStream.foreach(
          streamName = "test-stream",
          process = process(received))

        eventually {
          verify(metricsSender, never())
            .incrementCount("test-stream_ProcessMessage_failure")
          verify(metricsSender, times(3))
            .incrementCount("test-stream_ProcessMessage_recognisedFailure")
          received shouldBe empty

          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }
    }
  }

  it(
    "sends a failure metric if it doesn't fail gracefully when processing a message") {
    withSQSStreamFixtures {
      case (messageStream, QueuePair(queue, dlq), metricsSender) =>
        val exampleObject = NamedObject("some value 1")

        sendSqsMessage(queue, exampleObject)

        def processFailing(o: NamedObject) = {
          Future.failed(new RuntimeException("BOOOOM!"))
        }

        messageStream.foreach(
          streamName = "test-stream",
          process = processFailing)

        eventually {
          verify(metricsSender, times(3))
            .incrementCount(metricName = "test-stream_ProcessMessage_failure")
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
        }
    }
  }

  it("continues reading if processing of some messages fails") {
    withSQSStreamFixtures {
      case (messageStream, QueuePair(queue, dlq), _) =>
        sendNamedObjects(queue = queue, start = 1)
        sendInvalidJSONto(queue)

        sendNamedObjects(queue = queue, start = 2)
        sendInvalidJSONto(queue)

        val received = new ConcurrentLinkedQueue[NamedObject]()
        messageStream.foreach(
          streamName = "test-stream",
          process = process(received))

        eventually {
          received should contain theSameElementsAs createNamedObjects(
            count = 2)

          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 2)
        }
    }
  }

  describe("runStream") {
    it("processes messages off a queue ") {
      withSQSStreamFixtures {
        case (messageStream, QueuePair(queue, dlq), metricsSender) =>
          sendNamedObjects(queue = queue, start = 1, count = 2)

          val received = new ConcurrentLinkedQueue[NamedObject]()

          messageStream.runStream(
            "test-stream",
            source =>
              source.via(Flow.fromFunction {
                case (message, t) =>
                  received.add(t)
                  message
              }))

          eventually {
            received should contain theSameElementsAs createNamedObjects(
              count = 2)

            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)

            verify(metricsSender, times(2))
              .incrementCount("test-stream_ProcessMessage_success")
          }
      }
    }

    it("does not delete failed messages and sends a failure metric") {
      withSQSStreamFixtures {
        case (messageStream, QueuePair(queue, dlq), metricsSender) =>
          sendNamedObjects(queue = queue)

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
      withSQSStreamFixtures {
        case (messageStream, QueuePair(queue, dlq), _) =>
          sendNamedObjects(queue = queue, start = 1)
          sendInvalidJSONto(queue)

          sendNamedObjects(queue = queue, start = 2)
          sendInvalidJSONto(queue)

          val received = new ConcurrentLinkedQueue[NamedObject]()
          messageStream.runStream(
            "test-stream",
            source =>
              source.via(Flow.fromFunction {
                case (message, t) =>
                  received.add(t)
                  message
              }))

          eventually {
            received should contain theSameElementsAs createNamedObjects(
              count = 2)

            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, size = 2)
          }
      }
    }
  }

  private def createNamedObjects(start: Int = 1,
                                   count: Int): List[NamedObject] =
    (start until start + count).map { i =>
      NamedObject(s"Example value $i")
    }.toList

  private def sendNamedObjects(queue: Queue,
                                 start: Int = 1,
                                 count: Int = 1) =
    createNamedObjects(start = start, count = count).map { exampleObject =>
      sendSqsMessage(queue = queue, obj = exampleObject)
    }

  def withSQSStreamFixtures[R](
    testWith: TestWith[(SQSStream[NamedObject], QueuePair, MetricsSender),
                       R]): R =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueueAndDlq {
        case queuePair @ QueuePair(queue, _) =>
          withMockMetricsSender { metricsSender =>
            withSQSStream[NamedObject, R](queue, metricsSender) { stream =>
              testWith((stream, queuePair, metricsSender))
            }
          }
      }
    }
}
