package uk.ac.wellcome.messaging.sqsworker.alpakka

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.fixtures.worker.AlpakkaSQSWorkerFixtures

import scala.concurrent.ExecutionContext.Implicits.global

class AlpakkaSQSWorkerTest
    extends AnyFunSpec
    with Matchers
    with AlpakkaSQSWorkerFixtures
    with ScalaFutures
    with IntegrationPatience
    with Eventually
    with Akka {

  val namespace = "AlpakkaSQSWorkerTest"

  describe("When a message is processed") {
    it("consumes a message and increments success metrics") {
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withActorSystem { implicit actorSystem =>
            withAlpakkaSQSWorker(queue, successful, namespace) {
              case (worker, _, metrics, callCounter) =>
                worker.start

                val myWork = MyWork("my-new-work")

                sendNotificationToSQS(queue, myWork)

                eventually {
                  callCounter.calledCount shouldBe 1

                  assertMetricCount(
                    metrics = metrics,
                    metricName = s"$namespace/Successful",
                    expectedCount = 1
                  )
                  assertMetricDurations(
                    metrics = metrics,
                    metricName = s"$namespace/Duration",
                    expectedNumberDurations = 1
                  )

                  assertQueueEmpty(queue)
                  assertQueueHasSize(dlq, 0)
                }
            }
          }
      }
    }

    it(
      "consumes a message and increments non deterministic failure metrics metrics") {
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withActorSystem { implicit actorSystem =>
            withAlpakkaSQSWorker(queue, deterministicFailure, namespace) {
              case (worker, _, metrics, callCounter) =>
                worker.start

                val myWork = MyWork("my-new-work")

                sendNotificationToSQS(queue, myWork)

                eventually {
                  callCounter.calledCount shouldBe 1

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
                  assertQueueHasSize(dlq, 0)
                }
            }
          }
      }
    }

    it(
      "retries nonDeterministicFailure 3 times and places the message in the dlq") {
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withActorSystem { implicit actorSystem =>
            withAlpakkaSQSWorker(queue, nonDeterministicFailure, namespace) {
              case (worker, _, metrics, callCounter) =>
                worker.start

                val myWork = MyWork("my-new-work")

                sendNotificationToSQS(queue, myWork)

                eventually {
                  callCounter.calledCount shouldBe 3

                  assertMetricCount(
                    metrics = metrics,
                    metricName = s"$namespace/NonDeterministicFailure",
                    expectedCount = 3
                  )
                  assertMetricDurations(
                    metrics = metrics,
                    metricName = s"$namespace/Duration",
                    expectedNumberDurations = 3
                  )

                  assertQueueEmpty(queue)
                  assertQueueHasSize(dlq, 1)
                }
            }
          }
      }
    }
  }

  describe("When a message cannot be parsed") {
    it(
      "consumes the message increments failure metrics if the message is not json") {
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withActorSystem { implicit actorSystem =>
            withAlpakkaSQSWorker(queue, successful, namespace) {
              case (worker, _, metrics, _) =>
                worker.start

                sendNotificationToSQS(queue, "not json")

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

    it(
      "consumes the message increments failure metrics if the message is json but not a work") {
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withActorSystem { implicit actorSystem =>
            withAlpakkaSQSWorker(queue, successful, namespace) {
              case (worker, _, metrics, _) =>
                worker.start

                sendNotificationToSQS(queue, """{"json" : "but not a work"}""")

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
