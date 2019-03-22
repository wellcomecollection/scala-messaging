package uk.ac.wellcome.messaging.fixtures.worker

import akka.actor.ActorSystem
import com.amazonaws.services.sqs.AmazonSQSAsync
import org.scalatest.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sqsworker.alpakka.{AlpakkaSQSWorker, AlpakkaSQSWorkerConfig}


trait AlpakkaSQSWorkerFixtures
  extends WorkerFixtures
    with MetricsFixtures
    with Matchers {

  def withAlpakkaSQSWorker[R](
                               queue: Queue,
                               actorSystem: ActorSystem,
                               snsClient: AmazonSQSAsync,
                               process: MyProcess
                             )(
                               testWith: TestWith[(
                                 AlpakkaSQSWorker[
                                   MyWork,
                                   Option[String],
                                   MyProcess
                                   ], AlpakkaSQSWorkerConfig, MyMonitoringClient), R])(
    implicit fakeMonitoringClient: MyMonitoringClient = new MyMonitoringClient()
  ): R = {

    val alpakkaSQSWorkerConfigNamespace = "namespace"

    implicit val _snsClient = snsClient
    implicit val _actorSystem = actorSystem

    val config = AlpakkaSQSWorkerConfig(
      alpakkaSQSWorkerConfigNamespace,
      queue.url
    )

    val worker = new AlpakkaSQSWorker[
      MyWork,
      Option[String],
      MyProcess](config)(process)

    testWith( (worker, config, fakeMonitoringClient) )
  }
}
