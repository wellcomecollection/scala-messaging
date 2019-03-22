package uk.ac.wellcome.messaging.fixtures.worker

import java.time.Instant

import grizzled.slf4j.Logging
import org.scalatest.Matchers
import uk.ac.wellcome.messaging.worker._
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient

import scala.concurrent.{ExecutionContext, Future}

trait WorkerFixtures extends Matchers{
  type MySummary = Option[String]
  type TestResult = Result[MySummary]
  type TestProcess = MyWork => TestResult

  case class MyMessage(s: String)
  case class MyWork(s: String)

  object MyWork {
    def apply(message: MyMessage): MyWork =
      MyWork(message.s)
  }

  case class MyExternalMessageAction(action: Action)

  class MyWorker(
                  testProcess: TestProcess,
                  messageToWorkShouldFail: Boolean = false,
                  toActionShouldFail: Boolean = false,
                  monitoringClientShouldFail: Boolean = false
                )(implicit executionContext: ExecutionContext) extends Worker[MyMessage,MyWork, MySummary, MyProcess, MyExternalMessageAction] {
    override protected def toWork(
                                   message: MyMessage
                                 ): Future[MyWork] = messageToWork(
      messageToWorkShouldFail
    )(message)

    implicit val metrics = new MyMonitoringClient(monitoringClientShouldFail)

    override protected def toAction(action: Action): Future[MyExternalMessageAction] =
      actionToAction(toActionShouldFail)(action)

    def processMessage(id: String, message: MyMessage): Future[(MyMessage, MyExternalMessageAction)] = super.processMessage(id, message)

    override val process: MyProcess = new MyProcess(testProcess)
    override val namespace: String = "namespace"
  }

  class MyProcessor(testProcess: TestProcess, messageToWorkShouldFail: Boolean = false)(implicit executionContext: ExecutionContext) extends Processor[MyMessage, MyWork, MySummary, MyProcess] {
    override protected def toWork(message: MyMessage): Future[MyWork] =
      messageToWork(messageToWorkShouldFail)(message)
    override protected val process: MyProcess = new MyProcess(testProcess)
  }

  class MyPostProcessor(result: Result[_], toActionShouldFail: Boolean = false, monitoringClientShouldFail: Boolean = false) extends PostProcessor {
    implicit val monitoringClient = new MyMonitoringClient(monitoringClientShouldFail)

    def doPostProcess[ProcessMonitoringClient <: MonitoringClient](result: Result[_])(implicit ec: ExecutionContext): Future[Result[_]] = super.doPostProcess("id", Instant.now, result)(monitoringClient, ec)

    override val namespace: String = "namespace"
  }

  val message = MyMessage("some_content")
  val work = MyWork("some_content")

  def messageToWork(shouldFail: Boolean = false)(message: MyMessage)(implicit ec: ExecutionContext) = Future {
    if(shouldFail) {
      throw new RuntimeException("BOOM")
    } else {
      MyWork(message)
    }
  }

  def actionToAction(toActionShouldFail: Boolean)(action: Action)(implicit ec: ExecutionContext): Future[MyExternalMessageAction] = Future {
    if(toActionShouldFail) {
      throw new RuntimeException("BOOM")
    } else {
      MyExternalMessageAction(action)
    }
  }

  class MyProcess(testProcess: TestProcess)
    extends BaseOperation[MyWork, MySummary] {
    var called: Boolean = false

    override def run(
                      in: MyWork
                    )(implicit ec: ExecutionContext): Future[TestResult] = Future {
      called = true
      testProcess(in)
    }
  }

  class MyMonitoringClient(shouldFail: Boolean = false) extends MonitoringClient with Logging {
    var incrementCountCalls: Map[String, Int] = Map.empty
    var recordValueCalls: Map[String, List[Double]] = Map.empty

    override def incrementCount(metricName: String)(implicit ec: ExecutionContext): Future[Unit] = Future {
      info(s"MyMonitoringClient incrementing $metricName")
      if(shouldFail) { throw new RuntimeException("FakeMonitoringClient incrementCount Error!") }
      incrementCountCalls = incrementCountCalls + (metricName -> (incrementCountCalls.getOrElse(metricName, 0) + 1))
    }

    override def recordValue(metricName: String, value: Double)(implicit ec: ExecutionContext): Future[Unit] = Future {
      info(s"MyMonitoringClient recordValue $metricName: $value")
      if(shouldFail) { throw new RuntimeException("FakeMonitoringClient recordValue Error!") }
      recordValueCalls = recordValueCalls + (metricName -> (recordValueCalls.getOrElse(metricName, List.empty) :+ value))
    }
  }

  val successful: TestProcess = (in: MyWork) => Successful(
    in.toString,
    Some("Summary Successful")
  )

  val nonDeterministicFailure: TestProcess = (in: MyWork) => NonDeterministicFailure(
    in.toString,
    new RuntimeException("NonDeterministicFailure"),
    Some("Summary NonDeterministicFailure")
  )

  val deterministicFailure: TestProcess = (in: MyWork) => DeterministicFailure(
    in.toString,
    new RuntimeException("DeterministicFailure"),
    Some("Summary DeterministicFailure")
  )

  val postProcessFailure: TestProcess = (in: MyWork) => PostProcessFailure(
    in.toString,
    new RuntimeException("PostProcessFailure"),
    Some("Summary PostProcessFailure")
  )

  val exceptionState: TestProcess = (_: MyWork) => {
    throw new RuntimeException("BOOM")

    Successful("exceptionState")
  }

  val shouldBeSuccessful =
    (r: Result[_]) => r shouldBe a[Successful[_]]
  val shouldBeDeterministicFailure =
    (r: Result[_]) => r shouldBe a[DeterministicFailure[_]]
  val shouldBeNonDeterministicFailure =
    (r: Result[_]) => r shouldBe a[NonDeterministicFailure[_]]

  val shouldBeCompleted = (r: Result[_]) => r shouldBe a[Completed]
  val shouldBeRetry = (r: Result[_]) => r shouldBe a[Retry]
}