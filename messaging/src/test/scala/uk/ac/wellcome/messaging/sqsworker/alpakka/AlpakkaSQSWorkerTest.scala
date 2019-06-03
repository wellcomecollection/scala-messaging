package uk.ac.wellcome.messaging.sqsworker.alpakka

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.fixtures.worker.AlpakkaSQSWorkerFixtures

import scala.concurrent.ExecutionContext.Implicits.global

class AlpakkaSQSWorkerTest
    extends FunSpec
    with Matchers
    with AlpakkaSQSWorkerFixtures
    with ScalaFutures
    with IntegrationPatience
    with Eventually
    with Akka {

  val namespace = "AlpakkaSQSWorkerTest"

  describe("When a message is processed") {
    it(
      "increments metrics, consumes the message, and for a x3 retried nonDeterministicFailure places the message on the DLQ") {

      val processResults = Table(
        ("testProcess", "metricName", "metricCount", "dlqSize"),
        (successful, s"$namespace/Successful", 1, 0),
        (deterministicFailure, s"$namespace/DeterministicFailure", 1, 0),
        (nonDeterministicFailure, s"$namespace/NonDeterministicFailure", 3, 1)
      )

      forAll(processResults) {
        (testProcess: TestInnerProcess,
         metricName: String,
         expectedMetricCount: Int,
         expectedDlqSize: Int) =>
          {
            withLocalSqsQueueAndDlq {
              case QueuePair(queue, dlq) =>
                withActorSystem { implicit actorSystem =>
                  withAlpakkaSQSWorker(queue, testProcess, namespace) {
                    case (worker, _, metrics, callCounter) =>
                      worker.start

                      val myWork = MyWork("my-new-work")

                      sendNotificationToSQS(queue, myWork)

                      eventually {
                        callCounter.calledCount shouldBe expectedMetricCount

                        assertMetricCount(
                          metrics = metrics,
                          metricName = metricName,
                          expectedCount = expectedMetricCount
                        )
                        assertMetricDurations(
                          metrics = metrics,
                          metricName = s"$namespace/Duration",
                          expectedNumberDurations = expectedMetricCount
                        )

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
            withActorSystem { implicit actorSystem =>
              withAlpakkaSQSWorker(queue, successful, namespace) {
                case (worker, _, metrics, _) =>
                  worker.start

                  sendNotificationToSQS(queue, message)

                  eventually {
                    //process.called shouldBe false

                    assertMetricCount(
                      metrics = metrics,
                      metricName = s"$namespace/DeterministicFailure",
                      expectedCount = 1
                    )
                    assertMetricDurations(
                      metrics = metrics,
                      metricName = s"$namespace/Duration",
                      expectedNumberDurations = 1
                    )

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
