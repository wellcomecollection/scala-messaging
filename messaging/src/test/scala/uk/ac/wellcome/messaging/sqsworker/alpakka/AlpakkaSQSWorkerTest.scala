package uk.ac.wellcome.messaging.sqsworker.alpakka

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.{AlpakkaSQSWorkerFixtures, Messaging}
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture

class AlpakkaSQSWorkerTest extends FunSpec
  with Matchers
  with Messaging
  with Akka
  with AlpakkaSQSWorkerFixtures
  with ScalaFutures
  with IntegrationPatience
  with MetricsSenderFixture {

  describe("when a process succeeds") {
    it("calls the process required, increments metrics and deletes the message") {
      withLocalSqsQueue { queue =>
        withActorSystem { actorSystem =>
          val process = new FakeTestProcess(successful)

          withAlpakkaSQSWorker(queue, actorSystem, asyncSqsClient, process) {
            case (worker, config, metrics) =>

              worker.start
              sendNotificationToSQS[MyWork](queue, work)

              eventually {
                process.called shouldBe true

                metrics.incrementCountCalls shouldBe Map(
                  "namespace/Successful" -> 1
                )

                val durationMetric = metrics.recordValueCalls.get(
                  "namespace/Duration"
                )

                durationMetric shouldBe Some(_)
                durationMetric.get should have size (1)

                assertQueueEmpty(queue)
              }
          }
        }
      }
    }
  }
}
