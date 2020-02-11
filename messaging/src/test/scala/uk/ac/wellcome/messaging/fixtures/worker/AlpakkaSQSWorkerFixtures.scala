package uk.ac.wellcome.messaging.fixtures.worker

import java.time.Instant

import akka.actor.ActorSystem
import org.scalatest.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sqsworker.alpakka.{AlpakkaSQSWorker, AlpakkaSQSWorkerConfig}
import uk.ac.wellcome.messaging.worker.monitoring.metrics.MetricsMonitoringClient
import uk.ac.wellcome.monitoring.MetricsConfig

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.Random

trait AlpakkaSQSWorkerFixtures
  extends WorkerFixtures
    with MetricsFixtures
    with Matchers
    with SQS {

  def createAlpakkaSQSWorkerConfig(
    queue: Queue,
    namespace: String = Random.alphanumeric take 10 mkString): AlpakkaSQSWorkerConfig =
    AlpakkaSQSWorkerConfig(
      metricsConfig = MetricsConfig(namespace, flushInterval = 1.second),
      sqsConfig = createSQSConfigWith(queue)
    )

  def withAlpakkaSQSWorker[R](
    queue: Queue,
    process: TestInnerProcess,
    namespace: String = Random.alphanumeric take 10 mkString
  )(testWith: TestWith[(AlpakkaSQSWorker[MyWork, Instant, MySummary, MetricsMonitoringClient],
                        AlpakkaSQSWorkerConfig,
                        FakeMetricsMonitoringClient,
                        CallCounter),
                       R])(
    implicit
    as: ActorSystem,
    ec: ExecutionContext): R = {
    implicit val mc: FakeMetricsMonitoringClient = new FakeMetricsMonitoringClient()

    val config = createAlpakkaSQSWorkerConfig(queue, namespace)

    val callCounter = new CallCounter()
    val testProcess = (o: MyWork) =>
        createResult(process, callCounter)(ec)(o)

    val worker =
      new AlpakkaSQSWorker[MyWork, Instant, MySummary, MetricsMonitoringClient](config)(testProcess)

    testWith((worker, config, mc, callCounter))
  }
}
