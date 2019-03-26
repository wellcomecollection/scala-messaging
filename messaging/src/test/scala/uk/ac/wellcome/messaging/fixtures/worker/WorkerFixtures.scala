package uk.ac.wellcome.messaging.fixtures.worker

import java.time.Instant

import grizzled.slf4j.Logging
import org.scalatest.{Assertion, Matchers}
import uk.ac.wellcome.messaging.worker._
import uk.ac.wellcome.messaging.worker.models._
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient
import uk.ac.wellcome.messaging.worker.steps.{
  MessageProcessor,
  MonitoringProcessor
}

import scala.concurrent.{ExecutionContext, Future}

trait WorkerFixtures extends Matchers {
  type MySummary = String
  type TestResult = Result[MySummary]
  type TestInnerProcess = MyWork => TestResult
  type TestProcess = MyWork => Future[TestResult]

  object Conversions {
    import scala.language.implicitConversions

    implicit class IdentifiedMyMessage(message: MyMessage) {
      def toIdMessage: IdentifiedMessage[MyMessage] =
        IdentifiedMessage("id", message)
    }

    implicit def toIdMessage(
      message: MyMessage
    ): IdentifiedMessage[MyMessage] = message.toIdMessage
  }

  case class MyMessage(s: String)
  case class MyWork(s: String)

  object MyWork {
    def apply(message: MyMessage): MyWork =
      new MyWork(message.s)
  }

  class MyMonitoringClient(shouldFail: Boolean = false)
      extends MonitoringClient
      with Logging {
    var incrementCountCalls: Map[String, Int] = Map.empty
    var recordValueCalls: Map[String, List[Double]] = Map.empty

    override def incrementCount(metricName: String)(
      implicit ec: ExecutionContext): Future[Unit] = Future {
      info(s"MyMonitoringClient incrementing $metricName")
      if (shouldFail) {
        throw new RuntimeException(
          "FakeMonitoringClient incrementCount Error!")
      }
      incrementCountCalls = incrementCountCalls + (metricName -> (incrementCountCalls
        .getOrElse(metricName, 0) + 1))
    }

    override def recordValue(metricName: String, value: Double)(
      implicit ec: ExecutionContext): Future[Unit] = Future {
      info(s"MyMonitoringClient recordValue $metricName: $value")
      if (shouldFail) {
        throw new RuntimeException("FakeMonitoringClient recordValue Error!")
      }
      recordValueCalls = recordValueCalls + (metricName -> (recordValueCalls
        .getOrElse(metricName, List.empty) :+ value))
    }
  }

  def messageToWork(shouldFail: Boolean = false)(message: MyMessage)(
    implicit ec: ExecutionContext) = Future {
    if (shouldFail) {
      throw new RuntimeException("BOOM")
    } else {
      MyWork(message)
    }
  }

  def actionToAction(toActionShouldFail: Boolean)(result: Result[MySummary])(
    implicit ec: ExecutionContext): Future[MyExternalMessageAction] = Future {
    if (toActionShouldFail) {
      throw new RuntimeException("BOOM")
    } else {
      MyExternalMessageAction(result.asInstanceOf[Action])
    }
  }

  case class MyExternalMessageAction(action: Action)

  class MyWorker(
    testProcess: TestInnerProcess,
    messageToWorkShouldFail: Boolean = false,
    monitoringClientShouldFail: Boolean = false
  )(implicit executionContext: ExecutionContext)
      extends Worker[MyMessage, MyWork, MySummary, MyExternalMessageAction] {

    import Conversions._

    var calledCount = 0

    implicit val metrics = new MyMonitoringClient(monitoringClientShouldFail)

    override protected def transform(
      message: MyMessage
    ): Future[MyWork] = messageToWork(messageToWorkShouldFail)(message)

    override def processMessage(work: MyWork) = Future {
      synchronized {
        calledCount = calledCount + 1
      }

      testProcess(work)
    }

    def work[ProcessMonitoringClient <: MonitoringClient](
      id: String,
      message: MyMessage): Future[WorkCompletion[MyMessage, MySummary]] =
      super.work(message)

    override val namespace: String = "namespace"
  }

  class MyMessageProcessor(testProcess: TestInnerProcess,
                           messageToWorkShouldFail: Boolean = false)(
    implicit executionContext: ExecutionContext)
      extends MessageProcessor[MyMessage, MyWork, MySummary] {
    override protected def transform(message: MyMessage): Future[MyWork] =
      messageToWork(messageToWorkShouldFail)(message)

    override def processMessage(work: MyWork): Future[TestResult] =
      Future(testProcess(work))

    override def process(id: String)(message: MyMessage)(
      implicit ec: ExecutionContext): Future[Result[MySummary]] =
      super.process(id)(message)(ec)
  }

  class MyMonitoringProcessor(result: Result[_],
                              toActionShouldFail: Boolean = false,
                              monitoringClientShouldFail: Boolean = false)
      extends MonitoringProcessor {
    implicit val monitoringClient: MyMonitoringClient = new MyMonitoringClient(
      monitoringClientShouldFail)

    def record[ProcessMonitoringClient <: MonitoringClient](result: Result[_])(
      implicit ec: ExecutionContext): Future[Result[_]] =
      super.record("id")(Instant.now, result)(monitoringClient, ec)

    override val namespace: String = "namespace"
  }

  val message = MyMessage("some_content")
  val work = MyWork("some_content")

  class CallCounter() {
    var calledCount = 0
  }

  def createResult(op: TestInnerProcess,
                   callCounter: CallCounter): MyWork => TestResult = {

    val f = (in: MyWork) => {
      callCounter.calledCount = callCounter.calledCount + 1

      op(in)
    }

    f
  }

  val successful = (in: MyWork) => {
    Successful[MySummary](
      in.toString,
      Some("Summary Successful")
    )
  }

  val nonDeterministicFailure = (in: MyWork) =>
    NonDeterministicFailure[MySummary](
      in.toString,
      new RuntimeException("NonDeterministicFailure"),
      Some("Summary NonDeterministicFailure")
  )

  val deterministicFailure = (in: MyWork) =>
    DeterministicFailure[MySummary](
      in.toString,
      new RuntimeException("DeterministicFailure"),
      Some("Summary DeterministicFailure")
  )

  val monitoringProcessorFailure = (in: MyWork) =>
    MonitoringProcessorFailure[MySummary](
      in.toString,
      new RuntimeException("MonitoringProcessorFailure"),
      Some("Summary MonitoringProcessorFailure")
  )

  val exceptionState = (_: MyWork) => {
    throw new RuntimeException("BOOM")

    Successful[MySummary]("exceptionState")
  }

  val shouldBeSuccessful: Result[_] => Assertion =
    (r: Result[_]) => r shouldBe a[Successful[_]]
  val shouldBeDeterministicFailure: Result[_] => Assertion =
    (r: Result[_]) => r shouldBe a[DeterministicFailure[_]]
  val shouldBeNonDeterministicFailure: Result[_] => Assertion =
    (r: Result[_]) => r shouldBe a[NonDeterministicFailure[_]]
  val shouldBeMonitoringProcessorFailure: Result[_] => Assertion =
    (r: Result[_]) => r shouldBe a[MonitoringProcessorFailure[_]]

  val shouldBeCompleted: Result[_] => Assertion = (r: Result[_]) =>
    r shouldBe a[Completed]
  val shouldBeRetry: Result[_] => Assertion = (r: Result[_]) =>
    r shouldBe a[Retry]
}
