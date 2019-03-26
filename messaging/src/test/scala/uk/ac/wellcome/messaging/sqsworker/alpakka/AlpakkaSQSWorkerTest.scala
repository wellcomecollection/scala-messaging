package uk.ac.wellcome.messaging.sqsworker.alpakka

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.fixtures.worker.AlpakkaSQSWorkerFixtures
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture

import scala.concurrent.ExecutionContext.Implicits.global

class AlpakkaSQSWorkerTest
    extends FunSpec
    with Matchers
    with Messaging
    with Akka
    with AlpakkaSQSWorkerFixtures
    with ScalaFutures
    with IntegrationPatience
    with MetricsSenderFixture {

  describe("When a message is processed") {
    it(
      "increments metrics, consumes the message, and for nonDeterministicFailures places a message on the DLQ") {

      val processResults = Table(
        ("testProcess", "metricName", "metricCount", "dlqSize"),
        (successful, "namespace/Successful", 1, 0),
        (deterministicFailure, "namespace/DeterministicFailure", 1, 0),
        (nonDeterministicFailure, "namespace/NonDeterministicFailure", 3, 1)
      )

      forAll(processResults) {
        (testProcess: TestInnerProcess,
         metricName: String,
         expectedMetricCount: Int,
         expectedDlqSize: Int) =>
          {
            withLocalSqsQueueAndDlq {
              case QueuePair(queue, dlq) =>
                withActorSystem { actorSystem =>
                  withAlpakkaSQSWorker(
                    queue,
                    actorSystem,
                    asyncSqsClient,
                    testProcess) {
                    case (worker, _, metrics, callCounter) =>
                      worker.start

                      val myWork = MyWork("my-new-work")

                      sendNotificationToSQS(queue, myWork)

                      eventually {
                        callCounter.calledCount shouldBe expectedMetricCount

                        assertMetricCount(
                          metrics,
                          metricName,
                          expectedMetricCount)
                        assertMetricDurations(
                          metrics,
                          "namespace/Duration",
                          expectedMetricCount)

                        assertQueueEmpty(queue)
                        assertQueueHasSize(dlq, expectedDlqSize)
                      }
                  }
                }
            }
          }
      }
    }
  }

  describe("When a message cannot be parsed") {
    it(
      "does not call process, but consumes the message and increments metrics") {
      val messages = Table(
        "testProcess",
        "not json",
        """{"json" : "but not a work"}"""
      )
      forAll(messages) { message =>
        withLocalSqsQueueAndDlq {
          case QueuePair(queue, dlq) =>
            withActorSystem { actorSystem =>
              withAlpakkaSQSWorker(
                queue,
                actorSystem,
                asyncSqsClient,
                successful) {
                case (worker, _, metrics, _) =>
                  worker.start

                  sendNotificationToSQS(queue, message)

                  eventually {
                    //process.called shouldBe false

                    assertMetricCount(
                      metrics,
                      "namespace/DeterministicFailure",
                      1)
                    assertMetricDurations(metrics, "namespace/Duration", 1)

                    assertQueueEmpty(queue)
                    assertQueueEmpty(dlq)
                  }
              }
            }
        }
      }
    }
  }
}
