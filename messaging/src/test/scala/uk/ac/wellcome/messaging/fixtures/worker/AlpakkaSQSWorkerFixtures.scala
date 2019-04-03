package uk.ac.wellcome.messaging.fixtures.worker

import akka.actor.ActorSystem
import org.scalatest.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sqsworker.alpakka.{
  AlpakkaSQSWorker,
  AlpakkaSQSWorkerConfig
}

import scala.concurrent.{ExecutionContext, Future}
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
    process: TestInnerProcess
  )(testWith: TestWith[(AlpakkaSQSWorker[MyWork, MySummary],
                        AlpakkaSQSWorkerConfig,
                        FakeMonitoringClient,
                        CallCounter),
                       R])(
    implicit
    actorSystem: ActorSystem,
    ec: ExecutionContext): R = {
    implicit val fakeMonitoringClient: FakeMonitoringClient = new FakeMonitoringClient()

    val config = createAlpakkaSQSWorkerConfig(queue)

    val callCounter = new CallCounter()
    val testProcess = (o: MyWork) =>
      Future {
        createResult(process, callCounter)(o)
    }

    val worker =
      new AlpakkaSQSWorker[MyWork, MySummary](config)(testProcess)

    testWith((worker, config, fakeMonitoringClient, callCounter))
  }
}
