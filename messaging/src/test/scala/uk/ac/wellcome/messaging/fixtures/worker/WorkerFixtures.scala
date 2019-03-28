package uk.ac.wellcome.messaging.fixtures.worker

import java.time.Instant

import org.scalatest.{Assertion, Matchers}
import uk.ac.wellcome.messaging.worker._
import uk.ac.wellcome.messaging.worker.models._
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient
import uk.ac.wellcome.messaging.worker.steps.{MessageProcessor, MonitoringProcessor}

import scala.concurrent.{ExecutionContext, Future}

trait WorkerFixtures extends Matchers with MetricsFixtures {
  type MySummary = String
  type TestResult = Result[MySummary]
  type TestInnerProcess = MyWork => TestResult
  type TestProcess = MyWork => Future[TestResult]

  case class MyMessage(s: String)
  case class MyWork(s: String)

  object MyWork {
    def apply(message: MyMessage): MyWork =
      new MyWork(message.s)
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
      extends Worker[MyMessage, MyWork, MySummary] {

    var calledCount = 0

    implicit val metrics = new FakeMonitoringClient(monitoringClientShouldFail)

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

    override def process(message: MyMessage)(
      implicit ec: ExecutionContext): Future[Result[MySummary]] =
      super.process(message)(ec)
  }

  class MyMonitoringProcessor(result: Result[_],
                              toActionShouldFail: Boolean = false,
                              monitoringClientShouldFail: Boolean = false)(
                               implicit ec: ExecutionContext)
      extends MonitoringProcessor {
    implicit val monitoringClient: FakeMonitoringClient =
      new FakeMonitoringClient(monitoringClientShouldFail)

    def record[ProcessMonitoringClient <: MonitoringClient](result: Result[_])(
      implicit ec: ExecutionContext): Future[Result[_]] =
      super.record(Instant.now, result)(monitoringClient, ec)

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
      Some("Summary Successful")
    )
  }

  val nonDeterministicFailure = (in: MyWork) =>
    NonDeterministicFailure[MySummary](
      new RuntimeException("NonDeterministicFailure"),
      Some("Summary NonDeterministicFailure")
  )

  val deterministicFailure = (in: MyWork) =>
    DeterministicFailure[MySummary](
      new RuntimeException("DeterministicFailure"),
      Some("Summary DeterministicFailure")
  )

  val monitoringProcessorFailure = (in: MyWork) =>
    MonitoringProcessorFailure[MySummary](
      new RuntimeException("MonitoringProcessorFailure"),
      Some("Summary MonitoringProcessorFailure")
  )

  val exceptionState = (_: MyWork) => {
    throw new RuntimeException("BOOM")

    Successful[MySummary](Some("exceptionState"))
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
