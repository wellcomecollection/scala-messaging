package uk.ac.wellcome.messaging.fixtures.worker

import akka.actor.ActorSystem
import org.scalatest.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sqsworker.alpakka.{AlpakkaSQSWorker, AlpakkaSQSWorkerConfig}
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient

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
      namespace = namespace,
      queueUrl = queue.url
    )

  def withAlpakkaSQSWorker[R](
    queue: Queue,
    process: TestInnerProcess,
    namespace: String = Random.alphanumeric take 10 mkString
  )(testWith: TestWith[(AlpakkaSQSWorker[MyWork, MySummary, MonitoringClient],
                        AlpakkaSQSWorkerConfig,
                        FakeMonitoringClient,
                        CallCounter),
                       R])(
    implicit
    as: ActorSystem,
    ec: ExecutionContext): R = {
    implicit val mc: FakeMonitoringClient = new FakeMonitoringClient()

    val config = createAlpakkaSQSWorkerConfig(queue, namespace)

    val callCounter = new CallCounter()
    val testProcess = (o: MyWork) =>
        createResult(process, callCounter)(ec)(o)

    val worker =
      new AlpakkaSQSWorker[MyWork, MySummary, MonitoringClient](config)(testProcess)

    testWith((worker, config, mc, callCounter))
  }
}
