package uk.ac.wellcome.messaging.fixtures.worker

import java.time.Instant

import org.scalatest.{Assertion, Matchers}
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.fixtures.monitoring.metrics.MetricsFixtures
import uk.ac.wellcome.messaging.worker._
import uk.ac.wellcome.messaging.worker.models._
import uk.ac.wellcome.messaging.worker.monitoring.metrics.MetricsMonitoringProcessor
import uk.ac.wellcome.messaging.worker.steps.MessageProcessor
import uk.ac.wellcome.messaging.worker.monitoring.tracing.MonitoringContextSerializerDeserialiser

import scala.concurrent.{ExecutionContext, Future}

trait WorkerFixtures extends Matchers with MetricsFixtures {
  type MySummary = String
  type MyContext = Instant
  type TestResult = Result[MySummary]
  type TestInnerProcess = MyPayload => TestResult
  type TestProcess = MyPayload => Future[TestResult]
  type MyMessageAttributes = Map[String, String]

  case class MyMessage(s: String)
  case class MyPayload(s: String)

  object MyPayload {
    def apply(message: MyMessage): MyPayload =
      new MyPayload(message.s)
  }

  def messageToPayload(shouldFail: Boolean = false)(message: MyMessage)
    : (Either[Throwable, MyPayload], Either[Throwable, Option[MyContext]]) =
    if (shouldFail) {
      (Left(new RuntimeException("BOOM")), Right(None))
    } else {
      (Right(MyPayload(message)), Right(None))
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
    val monitoringProcessor: MetricsMonitoringProcessor[MyPayload],
    val messageSender: MessageSender[MyMessageAttributes],
    testProcess: TestInnerProcess,
    val deserialise: MyMessage => (Either[Throwable, MyPayload],
                                   Either[Throwable, Option[MyContext]])
  )(implicit val ec: ExecutionContext)
      extends Worker[
        MyMessage,
        MyPayload,
        MyContext,
        MyContext,
        MySummary,
        MyExternalMessageAction,
        MyMessageAttributes
      ] {

    val callCounter = new CallCounter()

    val monitoringSerialiser: MonitoringContextSerializerDeserialiser[
      MyContext,
      MyMessageAttributes] = ???

    override val retryAction: MessageAction =
      (_, MyExternalMessageAction(new Retry {}))

    override val completedAction: MessageAction =
      (_, MyExternalMessageAction(new Completed {}))

    override val doWork =
      (work: MyPayload) => createResult(testProcess, callCounter)(ec)(work)

    override type Completion = WorkCompletion[MyMessage, MySummary]
  }

  class MyMessageProcessor(
    testProcess: TestProcess
  ) extends MessageProcessor[MyPayload, MySummary] {

    override protected val doWork: TestProcess =
      testProcess
  }

  val message = MyMessage("some_content")
  val work = MyPayload("some_content")

  class CallCounter() {
    var calledCount = 0
  }

  def createResult(op: TestInnerProcess, callCounter: CallCounter)(
    implicit ec: ExecutionContext): MyPayload => Future[TestResult] = {

    (in: MyPayload) =>
      {
        callCounter.calledCount = callCounter.calledCount + 1

        Future(op(in))
      }
  }

  val successful = (in: MyPayload) => {
    Successful[MySummary](
      "Summary Successful"
    )
  }

  val nonDeterministicFailure = (in: MyPayload) =>
    NonDeterministicFailure[MySummary](
      new RuntimeException("NonDeterministicFailure")
  )

  val deterministicFailure = (in: MyPayload) =>
    DeterministicFailure[MySummary](
      new RuntimeException("DeterministicFailure")
  )

  val monitoringProcessorFailure = (in: MyPayload) =>
    MonitoringProcessorFailure[MySummary](
      new RuntimeException("MonitoringProcessorFailure")
  )

  val exceptionState = (_: MyPayload) => {
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
