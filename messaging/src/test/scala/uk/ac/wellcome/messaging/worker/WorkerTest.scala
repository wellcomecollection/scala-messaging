package uk.ac.wellcome.messaging.worker

import org.scalatest.Assertion
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.monitoring.metrics.MetricsFixtures
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures

import scala.concurrent.ExecutionContext.Implicits.global

class WorkerTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with ScalaFutures
    with IntegrationPatience
    with WorkerFixtures
    with MetricsFixtures {

  it("successfully processes a work and increments success metrics") {
    withMetricsMonitoringProcessor[MyWork, Unit](
      namespace = "namespace",
      shouldFail = false) {
      case (monitoringClient, monitoringProcessor) =>
        val worker = new MyWorker(
          monitoringProcessor,
          successful,
          messageToWork(shouldFail = false)
        )

        val process = worker.processMessage(message)
        whenReady(process) { _ =>
          worker.callCounter.calledCount shouldBe 1

          assertMetricCount(
            monitoringClient,
            "namespace/Successful",
            1,
          )

          assertMetricDurations(monitoringClient, "namespace/Duration", 1)
        }
    }
  }

  it("increments deterministic failure metric if transformation returns a Left") {
    withMetricsMonitoringProcessor[MyWork, Unit](
      namespace = "namespace",
      shouldFail = false) {
      case (monitoringClient, monitoringProcessor) =>
        val worker = new MyWorker(
          monitoringProcessor,
          successful,
          messageToWork(shouldFail = true)
        )

        val process = worker.processMessage(message)
        whenReady(process) { _ =>
          worker.callCounter.calledCount shouldBe 0

          assertMetricCount(
            monitoringClient,
            "namespace/DeterministicFailure",
            1,
          )

          assertMetricDurations(monitoringClient, "namespace/Duration", 1)
        }
    }
  }

  it(
    "increments deterministic failure metric if transformation fails unexpectedly") {
    def transform(message: MyMessage) = throw new RuntimeException

    withMetricsMonitoringProcessor[MyWork, Unit](
      namespace = "namespace",
      shouldFail = false) {
      case (monitoringClient, monitoringProcessor) =>
        val worker = new MyWorker(
          monitoringProcessor,
          successful,
          transform
        )

        val process = worker.processMessage(message)
        whenReady(process) { _ =>
          worker.callCounter.calledCount shouldBe 0

          assertMetricCount(
            monitoringClient,
            "namespace/DeterministicFailure",
            1,
          )

          assertMetricDurations(monitoringClient, "namespace/Duration", 1)
        }
    }
  }

  it("doesn't increment metrics if monitoring fails") {
    withMetricsMonitoringProcessor[MyWork, Assertion](
      namespace = "namespace",
      shouldFail = true) {
      case (monitoringClient, monitoringProcessor) =>
        val worker = new MyWorker(
          monitoringProcessor,
          successful,
          messageToWork(shouldFail = false)
        )

        val process = worker.processMessage(message)

        whenReady(process) { _ =>
          worker.callCounter.calledCount shouldBe 1

          monitoringClient.incrementCountCalls shouldBe Map.empty

          monitoringClient.recordValueCalls shouldBe Map.empty
        }
    }
  }

  it(
    "increments deterministic failure metric if processing fails with deterministic failure") {
    withMetricsMonitoringProcessor[MyWork, Unit](
      namespace = "namespace",
      shouldFail = false) {
      case (monitoringClient, monitoringProcessor) =>
        val worker = new MyWorker(
          monitoringProcessor,
          deterministicFailure,
          messageToWork(shouldFail = false)
        )

        val process = worker.processMessage(message)
        whenReady(process) { _ =>
          worker.callCounter.calledCount shouldBe 1

          assertMetricCount(
            monitoringClient,
            "namespace/DeterministicFailure",
            1,
          )

          assertMetricDurations(monitoringClient, "namespace/Duration", 1)
        }
    }
  }

  it(
    "increments non deterministic failure metric if processing fails with non deterministic failure") {
    withMetricsMonitoringProcessor[MyWork, Unit](
      namespace = "namespace",
      shouldFail = false) {
      case (monitoringClient, monitoringProcessor) =>
        val worker = new MyWorker(
          monitoringProcessor,
          nonDeterministicFailure,
          messageToWork(shouldFail = false)
        )

        val process = worker.processMessage(message)
        whenReady(process) { _ =>
          worker.callCounter.calledCount shouldBe 1

          assertMetricCount(
            monitoringClient,
            "namespace/NonDeterministicFailure",
            1,
          )

          assertMetricDurations(monitoringClient, "namespace/Duration", 1)
        }
    }
  }

}
