package uk.ac.wellcome.messaging.fixtures.worker

import akka.actor.ActorSystem
import com.amazonaws.services.sqs.AmazonSQSAsync
import org.scalatest.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sqsworker.alpakka.{AlpakkaSQSWorker, AlpakkaSQSWorkerConfig}

import scala.concurrent.{ExecutionContext, Future}


trait AlpakkaSQSWorkerFixtures
  extends WorkerFixtures
    with MetricsFixtures
    with Matchers {

  def withAlpakkaSQSWorker[R](
                               queue: Queue,
                               actorSystem: ActorSystem,
                               snsClient: AmazonSQSAsync,
                               process: TestInnerProcess
                             )(
                               testWith: TestWith[(
                                 AlpakkaSQSWorker[
                                   MyWork,
                                   MySummary], AlpakkaSQSWorkerConfig, MyMonitoringClient,
                                   CallCounter), R])(
    implicit fakeMonitoringClient: MyMonitoringClient = new MyMonitoringClient(), ec: ExecutionContext
  ): R = {

    val alpakkaSQSWorkerConfigNamespace = "namespace"

    implicit val _snsClient = snsClient
    implicit val _actorSystem = actorSystem

    val config = AlpakkaSQSWorkerConfig(
      alpakkaSQSWorkerConfigNamespace,
      queue.url
    )

    val callCounter = new CallCounter()
    val testProcess = (o: MyWork) => Future {
      createResult(process, callCounter)(o)
    }

    val worker =
      new AlpakkaSQSWorker[MyWork, MySummary](config)(testProcess)

    testWith( (worker, config, fakeMonitoringClient, callCounter) )
  }
}
