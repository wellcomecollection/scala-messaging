package uk.ac.wellcome.messaging.fixtures.worker

import java.time.Instant

import grizzled.slf4j.Logging
import org.scalatest.{Assertion, Matchers}
import uk.ac.wellcome.messaging.worker._
import uk.ac.wellcome.messaging.worker.models._
import uk.ac.wellcome.messaging.worker.monitoring.metrics.{MetricsMonitoringClient, MetricsMonitoringProcessor}
import uk.ac.wellcome.messaging.worker.steps.MessageProcessor

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

  class MyMetricsMonitoringClient(shouldFail: Boolean = false)(implicit ec: ExecutionContext)
      extends MetricsMonitoringClient
      with Logging {

    var incrementCountCalls: Map[String, Int] = Map.empty
    var recordValueCalls: Map[String, List[Double]] = Map.empty

    override def incrementCount(metricName: String): Future[Unit] = Future {

      info(s"MyMonitoringClient incrementing $metricName")

      if (shouldFail) {
        throw new RuntimeException(
          "FakeMonitoringClient incrementCount Error!")
      }

      incrementCountCalls =
        incrementCountCalls + (
          metricName -> (
            incrementCountCalls
              .getOrElse(metricName, 0) + 1
            )
          )
    }

    override def recordValue(metricName: String, value: Double): Future[Unit] = Future {

      info(
        s"MyMonitoringClient recordValue $metricName: $value"
      )

      if (shouldFail) {
        throw new RuntimeException(
          "FakeMonitoringClient recordValue Error!"
        )
      }

      recordValueCalls =
        recordValueCalls + (
          metricName -> (
            recordValueCalls.getOrElse(
              metricName,
              List.empty
            ) :+ value)
          )
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
  )(implicit val ec: ExecutionContext)
      extends Worker[
        MyMessage, MyWork, MySummary, MyExternalMessageAction
      ] {

    val callCounter = new CallCounter()

    implicit val mc: FakeMetricsMonitoringClient =
      new FakeMetricsMonitoringClient(monitoringClientShouldFail)

    override val retryAction: MessageAction =
        (_, MyExternalMessageAction(new Retry {}))

    override val completedAction: MessageAction =
      (_, MyExternalMessageAction(new Completed {}))

    override val transform: MyMessage => Future[MyWork] = {
      message =>

      messageToWork(messageToWorkShouldFail)(message)
    }

    override val doWork =
      (work: MyWork) => createResult(testProcess, callCounter)(ec)(work)


    override def processMessage(message: MyMessage): Processed =
      super.processMessage(message)

    override type Completion = WorkCompletion[MyMessage, MySummary]

    override val namespace: String = "namespace"
  }

  class MyMessageProcessor(
    testProcess: TestProcess,
    messageToWorkShouldFail: Boolean = false
  )(implicit
    ec: ExecutionContext
  ) extends MessageProcessor[MyMessage, MyWork, MySummary] {

    type Transform = MyMessage => Future[MyWork]

    override def process(message: MyMessage)(implicit ec: ExecutionContext): ResultSummary = super.process(message)

    override val transform: Transform =
      messageToWork(messageToWorkShouldFail)(_)

    override val doWork: TestProcess =
      testProcess
  }

  class MyMonitoringProcessor(
    toActionShouldFail: Boolean = false,
    monitoringClientShouldFail: Boolean = false
  )(implicit ec: ExecutionContext) extends MetricsMonitoringProcessor {

    implicit val mc =
      new FakeMetricsMonitoringClient(monitoringClientShouldFail)

    def record[ProcessMonitoringClient <: MetricsMonitoringClient](
      result: Result[_]
    )(implicit
        ec: ExecutionContext
    ): Future[Result[_]] =
      super.record(Instant.now, result)(mc, ec)

    override val namespace: String = "namespace"
  }

  val message = MyMessage("some_content")
  val work = MyWork("some_content")

  class CallCounter() {
    var calledCount = 0
  }

  def createResult(op: TestInnerProcess,
                   callCounter: CallCounter)(implicit ec: ExecutionContext): MyWork => Future[TestResult] = {

    (in: MyWork) => {
      callCounter.calledCount = callCounter.calledCount + 1

      Future(op(in))
    }
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
