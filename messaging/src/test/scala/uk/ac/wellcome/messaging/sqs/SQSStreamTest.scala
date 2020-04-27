package uk.ac.wellcome.messaging.sqs

import java.util.concurrent.ConcurrentLinkedQueue

import akka.stream.scaladsl.Flow
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.{RandomGenerators, TestWith}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.monitoring.memory.MemoryMetrics

import scala.concurrent.Future

class SQSStreamTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with Eventually
    with SQS
    with Akka
    with RandomGenerators {

  case class NamedObject(name: String)

  def process(list: ConcurrentLinkedQueue[NamedObject])(
    o: NamedObject): Future[Unit] = {
    list.add(o)
    Future.successful(())
  }

  it("reads messages off a queue, processes them and deletes them") {
    withSQSStreamFixtures {
      case (messageStream, QueuePair(queue, dlq), _) =>
        sendNamedObjects(queue = queue, count = 3)

        val received = new ConcurrentLinkedQueue[NamedObject]()
        val streamName = randomAlphanumeric(10)
        messageStream.foreach(
          streamName = streamName,
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
        val streamName = randomAlphanumeric(10)
        messageStream.foreach(
          streamName = streamName,
          process = process(received))

        eventually {
          metricsSender.incrementedCounts shouldBe Seq(
            s"${streamName}_ProcessMessage_success")
        }
    }
  }

  it("fails gracefully when the object cannot be deserialised") {
    withSQSStreamFixtures {
      case (messageStream, QueuePair(queue, dlq), metricsSender) =>
        sendInvalidJSONto(queue)

        val received = new ConcurrentLinkedQueue[NamedObject]()
        val streamName = randomAlphanumeric(10)
        messageStream.foreach(
          streamName = streamName,
          process = process(received))

        eventually {
          metricsSender.incrementedCounts shouldBe Seq(
            s"${streamName}_ProcessMessage_recognisedFailure",
            s"${streamName}_ProcessMessage_recognisedFailure",
            s"${streamName}_ProcessMessage_recognisedFailure"
          )
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
        val streamName = randomAlphanumeric(10)

        messageStream.foreach(streamName = streamName, process = processFailing)

        eventually {
          metricsSender.incrementedCounts shouldBe Seq(
            s"${streamName}_ProcessMessage_failure",
            s"${streamName}_ProcessMessage_failure",
            s"${streamName}_ProcessMessage_failure"
          )
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
        val streamName = randomAlphanumeric(10)

        messageStream.foreach(
          streamName = streamName,
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
          val streamName = randomAlphanumeric(10)

          messageStream.runStream(
            streamName,
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

            metricsSender.incrementedCounts shouldBe Seq(
              s"${streamName}_ProcessMessage_success",
              s"${streamName}_ProcessMessage_success"
            )
          }
      }
    }

    it("does not delete failed messages and sends a failure metric") {
      withSQSStreamFixtures {
        case (messageStream, QueuePair(queue, dlq), metricsSender) =>
          sendNamedObjects(queue = queue)
          val streamName = randomAlphanumeric(10)

          messageStream.runStream(
            streamName,
            source =>
              source.via(
                Flow.fromFunction(_ => throw new RuntimeException("BOOOM!"))))

          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)

            metricsSender.incrementedCounts shouldBe Seq(
              s"${streamName}_ProcessMessage_failure",
              s"${streamName}_ProcessMessage_failure",
              s"${streamName}_ProcessMessage_failure"
            )
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
            randomAlphanumeric(10),
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

  private def sendNamedObjects(queue: Queue, start: Int = 1, count: Int = 1) =
    createNamedObjects(start = start, count = count).map { exampleObject =>
      sendSqsMessage(queue = queue, obj = exampleObject)
    }

  def withSQSStreamFixtures[R](
    testWith: TestWith[(SQSStream[NamedObject],
                        QueuePair,
                        MemoryMetrics[StandardUnit]),
                       R]): R =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueueAndDlq {
        case queuePair @ QueuePair(queue, _) =>
          val metrics = new MemoryMetrics[StandardUnit]()
          withSQSStream[NamedObject, R](queue, metrics) { stream =>
            testWith((stream, queuePair, metrics))
          }
      }
    }
}
