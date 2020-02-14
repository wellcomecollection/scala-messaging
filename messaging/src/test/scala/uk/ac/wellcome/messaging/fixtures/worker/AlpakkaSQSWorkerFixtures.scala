package uk.ac.wellcome.messaging.fixtures.worker

import akka.actor.ActorSystem
import org.scalatest.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.fixtures.monitoring.metrics.MetricsFixtures
import uk.ac.wellcome.messaging.sqsworker.alpakka.{AlpakkaSQSWorker, AlpakkaSQSWorkerConfig}
import uk.ac.wellcome.monitoring.MetricsConfig

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
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
  )(testWith: TestWith[(AlpakkaSQSWorker[MyWork, MyContext, MySummary],
                        AlpakkaSQSWorkerConfig,
                        FakeMetricsMonitoringClient,
                        CallCounter),
                       R])(
    implicit
    as: ActorSystem,
    ec: ExecutionContext): R =

    withMetricsMonitoringProcessor[MyWork, R](namespace, false) { case (monitoringClient, monitoringProcessor) =>
      implicit val mp = monitoringProcessor

      val config = createAlpakkaSQSWorkerConfig(queue, namespace)

      val callCounter = new CallCounter()
      val testProcess = (o: MyWork) =>
        createResult(process, callCounter)(ec)(o)

      val worker =
        new AlpakkaSQSWorker[MyWork, MyContext, MySummary](config)(testProcess)

      testWith((worker, config, monitoringClient, callCounter))
    }
}
