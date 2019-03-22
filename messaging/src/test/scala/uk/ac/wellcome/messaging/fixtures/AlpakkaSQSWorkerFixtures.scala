package uk.ac.wellcome.messaging.fixtures

import akka.actor.ActorSystem
import com.amazonaws.services.sqs.AmazonSQSAsync
import grizzled.slf4j.Logging
import org.scalatest.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sqsworker.alpakka.{AlpakkaSQSWorker, AlpakkaSQSWorkerConfig}
import uk.ac.wellcome.messaging.worker._
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient
import uk.ac.wellcome.json.JsonUtil._

import scala.concurrent.{ExecutionContext, Future}

trait AlpakkaSQSWorkerFixtures extends Matchers {
  case class MyWork(s: String)

  type TestResult = Result[Option[String]]
  type TestProcess = MyWork => TestResult

  class FakeMonitoringClient(shouldFail: Boolean = false) extends MonitoringClient with Logging {
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

  val work = MyWork("some_content")

  class FakeTestProcess(testProcess: TestProcess)
    extends WorkerProcess[MyWork, Option[String]] {
    var called: Boolean = false

    override def run(
                      in: MyWork
                    )(implicit ec: ExecutionContext): Future[TestResult] = Future {
      called = true
      testProcess(in)
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

  val exceptionState: TestProcess = (in: MyWork) => {
    Future.failed(new RuntimeException("BOOM"))
    Successful("exceptionState")
  }

  def withAlpakkaSQSWorker[R](
                               queue: Queue,
                               actorSystem: ActorSystem,
                               snsClient: AmazonSQSAsync,
                               process: FakeTestProcess
                             )(
                               testWith: TestWith[(
                                 AlpakkaSQSWorker[
                                   FakeMonitoringClient,
                                   MyWork,
                                   Option[String],
                                   FakeTestProcess
                                   ], AlpakkaSQSWorkerConfig, FakeMonitoringClient), R])(
    implicit fakeMonitoringClient: FakeMonitoringClient = new FakeMonitoringClient()
  ): R = {

    val alpakkaSQSWorkerConfigNamespace = "namespace"

    implicit val _snsClient = snsClient
    implicit val _actorSystem = actorSystem

    val config = AlpakkaSQSWorkerConfig(
      alpakkaSQSWorkerConfigNamespace,
      queue.url
    )

    val worker = new AlpakkaSQSWorker[
      FakeMonitoringClient,
      MyWork,
      Option[String],
      FakeTestProcess](config)(process)

    testWith( (worker, config, fakeMonitoringClient) )
  }

  protected def assertMetricCount(metrics: FakeMonitoringClient, metricName : String, expectedCount : Int) = {
    metrics.incrementCountCalls shouldBe Map(
      metricName -> expectedCount
    )
  }

  protected def assertMetricDurations(metrics: FakeMonitoringClient, metricName: String, expectedNumberDurations: Int) = {
    val durationMetric = metrics.recordValueCalls.get(
      metricName
    )

    durationMetric shouldBe defined
    durationMetric.get should have length expectedNumberDurations
    durationMetric.get.foreach(_ should be >= 0.0)
  }
}
