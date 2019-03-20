package uk.ac.wellcome.messaging.sqs

import java.util.concurrent.ConcurrentLinkedQueue

import akka.stream.scaladsl.Flow
import grizzled.slf4j.Logging
import org.mockito.Mockito.{atLeastOnce, never, times, verify}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import uk.ac.wellcome.json.JsonUtil._

import scala.concurrent.Future

class SQSStreamTest
  extends FunSpec
    with Matchers
    with Messaging
    with Akka
    with ScalaFutures
    with IntegrationPatience
    with MetricsSenderFixture {

  def process(list: ConcurrentLinkedQueue[ExampleObject])(o: ExampleObject) = {
    list.add(o)
    Future.successful(())
  }

  it("reads messages off a queue, processes them and deletes them") {
    withSQSStreamFixtures {
      case (messageStream, QueuePair(queue, dlq), _) =>
        sendExampleObjects(queue = queue, count = 3)

        val received = new ConcurrentLinkedQueue[ExampleObject]()

        messageStream.foreach(
          streamName = "test-stream",
          process = process(received))

        eventually {
          received should contain theSameElementsAs createExampleObjects(
            count = 3)

          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }

  }

  it("increments *_ProcessMessage metric when successful") {
    withSQSStreamFixtures {
      case (messageStream, QueuePair(queue, _), metricsSender) =>
        sendExampleObjects(queue = queue)

        val received = new ConcurrentLinkedQueue[ExampleObject]()
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

        val received = new ConcurrentLinkedQueue[ExampleObject]()

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
        val exampleObject = ExampleObject("some value 1")

        sendSqsMessage(queue, exampleObject)
        def processFailing(o: ExampleObject) = {
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
        sendExampleObjects(queue = queue, start = 1)
        sendInvalidJSONto(queue)

        sendExampleObjects(queue = queue, start = 2)
        sendInvalidJSONto(queue)

        val received = new ConcurrentLinkedQueue[ExampleObject]()
        messageStream.foreach(
          streamName = "test-stream",
          process = process(received))

        eventually {
          received should contain theSameElementsAs createExampleObjects(
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
          sendExampleObjects(queue = queue, start = 1, count = 2)

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
            received should contain theSameElementsAs createExampleObjects(
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
          sendExampleObjects(queue = queue)

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
          sendExampleObjects(queue = queue, start = 1)
          sendInvalidJSONto(queue)

          sendExampleObjects(queue = queue, start = 2)
          sendInvalidJSONto(queue)

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
            received should contain theSameElementsAs createExampleObjects(
              count = 2)

            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, size = 2)
          }
      }
    }
  }

  private def createExampleObjects(start: Int = 1,
                                   count: Int): List[ExampleObject] =
    (start to (start + count - 1)).map { i =>
      ExampleObject(s"Example value $i")
    }.toList

  private def sendExampleObjects(queue: Queue, start: Int = 1, count: Int = 1) =
    createExampleObjects(start = start, count = count).map { exampleObject =>
      sendSqsMessage(queue = queue, obj = exampleObject)
    }

  def withSQSStreamFixtures[R](
                                testWith: TestWith[(SQSStream[ExampleObject], QueuePair, MetricsSender), R])
  : R =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueueAndDlq {
        case queuePair @ QueuePair(queue, _) =>
          withMockMetricsSender { metricsSender =>
            withSQSStream[ExampleObject, R](queue, metricsSender) { stream =>
              testWith((stream, queuePair, metricsSender))
            }
          }
      }
    }

  import java.time.{Instant, Duration => JavaDuration}
  import java.util.UUID

  import akka.actor.{ActorSystem, Cancellable}

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.{Await, Future}
  import scala.concurrent.duration.{Duration, FiniteDuration}


  trait Message[MessageId] {
    val id: MessageId
  }

  case class Summary[ContextId](
                                 id: ContextId,
                                 description: String
                               )

  sealed trait Result[MessageId] {
    val messageId: MessageId
  }

  trait SummarisableResult[MessageId, ContextId] extends Result[MessageId] {
    val messageId: MessageId
    val summary: Summary[ContextId]
  }

  trait Completed[MessageId, ContextId]
    extends SummarisableResult[MessageId, ContextId] {
  }

  trait Retry[MessageId, ContextId]
    extends SummarisableResult[MessageId, ContextId]

  case class Successful[MessageId, ContextId](
                                               messageId: MessageId,
                                               summary: Summary[ContextId]
                                             ) extends Completed[MessageId, ContextId]  {
    override def toString: String =
      s"""
         |Successful: $messageId
         |Contextual Id: ${summary.id}
         |${summary.description}
       """.stripMargin
  }

  case class UnknownFailure[MessageId](
                                        messageId: MessageId,
                                        failure: Throwable
                                      ) extends Result[MessageId] {
    override def toString: String =
      s"""
         |UnknownFailure: $messageId
         |Failure: ${failure.getMessage}
       """.stripMargin
  }

  case class MonitoringUpdateFailure[MessageId](
                                        messageId: MessageId,
                                        failure: Throwable
                                      ) extends Result[MessageId] {
    override def toString: String =
      s"""
         |MonitoringUpdateFailure: $messageId
         |Failure: ${failure.getMessage}
       """.stripMargin
  }

  case class DeterministicFailure[
  MessageId,
  ContextId,
  FailureType
  ](
     messageId: MessageId,
     failure: FailureType,
     summary: Summary[ContextId]
   ) extends Completed[MessageId, ContextId] {
    override def toString: String =
      s"""
         |DeterministicFailure: $messageId
         |Contextual Id: ${summary.id}
         |Failure: $failure
         |${summary.description}
       """.stripMargin
  }

  case class NonDeterministicFailure[MessageId, ContextId, FailureType](
                                                                         messageId: MessageId,
                                                                         failure: FailureType,
                                                                         summary: Summary[ContextId]
                                                                       ) extends Retry[MessageId, ContextId] {
    override def toString: String =
      s"""
         |NonDeterministicFailure: $messageId
         |Contextual Id: ${summary.id}
         |Failure: $failure
         |${summary.description}
       """.stripMargin
  }

  trait Process[
  MessageId,
  ContextId,
  IdentifiedMessage <: Message[MessageId]
  ] {

    def run(in: IdentifiedMessage): Future[SummarisableResult[MessageId, ContextId]]
  }

  trait MessageClient[MessageId, IdentifiedMessage <: Message[MessageId]] {
    def delete(id: MessageId): Future[MessageId]
    def retry(id: MessageId): Future[MessageId]
    def receive: Future[Seq[IdentifiedMessage]]
  }

  trait Scheduling {
    val system: ActorSystem

    val startAt: FiniteDuration = Duration(1, "seconds")
    val interval: FiniteDuration

    val maxWait: Duration = Duration.Inf

    def schedule(f: => Future[Unit]): Cancellable = {

      system.scheduler.schedule(startAt, interval) {
        Await.result(f, maxWait)
      }
    }
  }

  trait MonitoringClient {
    def incrementCount(metricName: String): Future[Unit]
    def recordValue(metricName: String, value: Double): Future[Unit]
  }

  class ProcessMonitor[ProcessMonitoringClient <: MonitoringClient](monitoringClient: ProcessMonitoringClient, namespace: String) {

    private def metricName(name: String) = s"$namespace/$name"

    def monitor[ProcessResult <: Result[_]] (result: ProcessResult, duration: JavaDuration) = {
      val countResult = result match {
        case _: Successful[_, _] => monitoringClient
          .incrementCount(metricName("Successful"))
        case _: UnknownFailure[_] => monitoringClient
          .incrementCount(metricName("UnknownFailure"))
        case _: DeterministicFailure[_, _, _] => monitoringClient
          .incrementCount(metricName("DeterministicFailure"))
        case _: NonDeterministicFailure[_, _, _] => monitoringClient
          .incrementCount(metricName("NonDeterministicFailure"))
      }

      val recordDuration =
        monitoringClient.recordValue(
          metricName("Duration"),
          duration.getSeconds
        )

      Future.sequence(
        List(countResult, recordDuration)
      )
    }
  }

  abstract class Worker[
  MessageId,
  ContextId,
  MessagingClient <: MessageClient[MessageId, IdentifiedMessage],
  ProcessMonitoringClient <: MonitoringClient,
  IdentifiedMessage <: Message[MessageId],
  MessageProcess <: Process[
    MessageId,
    ContextId,
    IdentifiedMessage]
  ] extends Scheduling with Logging {

    val retryOnUnknownFailure: Boolean = true

    val process: MessageProcess
    val messagingClient: MessagingClient
    val processMonitor: ProcessMonitor[ProcessMonitoringClient]

    private def logResultSummary[ProcessResult <: Result[_]] (result: ProcessResult): Unit = result match {
      case r: Successful[_,_] => info(r.toString)
      case r: UnknownFailure[_] => error(r.toString)
      case r: DeterministicFailure[_,_,_] => error(r.toString)
      case r: NonDeterministicFailure[_,_,_] => warn(r.toString)
    }

    private def updateMessageStatus(
                                     result: Result[MessageId]
                                   ) = result match {

      case o: SummarisableResult[MessageId, ContextId] => o match {
        case o: Completed[MessageId, ContextId] => {
          info(
            s"Completed ${result.messageId}"
          )

          messagingClient.delete(o.messageId)
        }

        case o: Retry[MessageId, ContextId] => {
          warn(
            s"Retrying ${result.messageId}"
          )

          messagingClient.retry(o.messageId)
        }
      }

      case o: UnknownFailure[MessageId] => {

        if(retryOnUnknownFailure) {
          warn(
            s"Retrying ${result.messageId}"
          )

          messagingClient.retry(o.messageId)
        } else {
          info(
            s"Completed ${result.messageId}"
          )

          messagingClient.delete(o.messageId)
        }
      }
    }

    private def run(
                     message: IdentifiedMessage
                   ) = {
      debug(s"Received $message")

      val startTime = Instant.now()

      for {
        result <- process.run(message).recover {
          case e => UnknownFailure(message.id, e)
        }

        processDuration = JavaDuration.between(startTime, Instant.now())

        _ = logResultSummary(result)

        _ <- updateMessageStatus(result).recover {
          case e => error(
            s"Error updating message status for ${message.id}: $e"
          )
        }
        _ <- processMonitor.monitor(result, processDuration).recover {
          case e => error(
            s"Error updating process monitor for ${message.id}: $e"
          )
        }

      } yield ()
    }

    def start: Cancellable = {
      schedule {
        debug("Scheduling message batch processing")

        for {
          messages <- messagingClient
            .receive
            .recover {
              case e => {
                error("Error receiving messages", e)

                Seq.empty
              }
            }

          _ = debug(s"Processing ${messages.length} messages")

          _ <- Future
            .sequence(messages.map(run))
            .recover {
              case e => {
                error("Unexpected error processing message batch", e)

                ()
              }
            }

          _ = debug(s"Finished processing messages")

        } yield ()
      }
    }
  }

  case class MyMessage(
                        id: UUID,
                        body: String
                      ) extends Message[UUID]

  class MyMessageClient() extends MessageClient[UUID, MyMessage] with Logging {

    def delete(id: UUID): Future[UUID] = Future {
      info(s"MyMessageClient delete: $id")

      if(math.random > 0.9) {
        info(s"Throwing error doing delete: $id")
        throw new RuntimeException("MyMessageClient Error!")
      }

      id
    }
    def retry(id: UUID): Future[UUID] = Future {
      info(s"MyMessageClient retry: $id ")

      if(math.random > 0.9) {
        info(s"Throwing error doing retry: $id")
        throw new RuntimeException("MyMessageClient Error!")
      }

      id
    }
    def receive: Future[Seq[MyMessage]] = Future {
      val messages = List(
        MyMessage(UUID.randomUUID(), Instant.now.toString)
      )

      info(s"MyMessageClient receive: $messages")

      if(math.random > 0.9) {
        info(s"Throwing error doing receive")
        throw new RuntimeException("MyMessageClient Error!")
      }

      messages
    }
  }

  class MyProcess()
    extends Process[UUID,UUID,MyMessage] {
    def run(in: MyMessage) = {
      info(s"MyProcess run $in")

      val waitTimeMillis = (math.random * 100).toInt

      math.random match {
        case n if n < 0.25 => Future {
          Thread.sleep(waitTimeMillis)

          Successful(
            UUID.randomUUID(),
            Summary(
              UUID.randomUUID(),
              "Summary Successful"
            )
          )
        }
        case n if n >= 0.25 && n < 0.5 => Future {
          Thread.sleep(waitTimeMillis)

          NonDeterministicFailure(
            UUID.randomUUID(),
            "done goofed",
            Summary(
              UUID.randomUUID(),
              "Summary NonDeterministicFailure"
            )
          )
        }
        case n if n >= 0.5 && n <=0.75 => Future {
          Thread.sleep(waitTimeMillis)

          DeterministicFailure(
            UUID.randomUUID(),
            "done goofed good",
            Summary(
              UUID.randomUUID(),
              "Summary DeterministicFailure"
            )
          )
        }
        case n if n > 0.75 => {
          Thread.sleep(waitTimeMillis)

          Future.failed(new RuntimeException("BOOM"))
        }
      }
    }
  }

  class MyMonitoringClient extends MonitoringClient with Logging{
    override def incrementCount(metricName: String): Future[Unit] = Future {
      info(s"MyMonitoringClient incrementing $metricName")

      if(math.random > 0.9) {
        info(s"Throwing error doing incrementCount")
        throw new RuntimeException("MyMessageClient Error!")
      }
    }

    override def recordValue(metricName: String, value: Double): Future[Unit] = Future {
      info(s"MyMonitoringClient recordValue $metricName: $value")

      if(math.random > 0.9) {
        info(s"Throwing error doing incrementCount")
        throw new RuntimeException("MyMessageClient Error!")
      }
    }
  }

  class MyWorker() extends Worker[
    UUID, UUID,
    MyMessageClient,
    MyMonitoringClient,
    MyMessage,
    MyProcess] {

    override val system = ActorSystem("my-actor-system")
    override val interval = Duration(100, "milliseconds")

    override val process = new MyProcess()
    override val messagingClient = new MyMessageClient()
    override val processMonitor = new ProcessMonitor(
      new MyMonitoringClient(),
      namespace = "my-namespace"
    )
  }

  it("fails") {
    val myWorker = new MyWorker()

    myWorker.start

    Thread.sleep(30000)
  }
}
