package uk.ac.wellcome.messaging.worker

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.monitoring.metrics.MetricsFixtures
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture

import scala.concurrent.ExecutionContext.Implicits.global

class WorkerTest
    extends FunSpec
    with Matchers
    with Akka
    with ScalaFutures
    with IntegrationPatience
    with WorkerFixtures
    with MetricsFixtures
    with MetricsSenderFixture {

  it("successfully processes a work and increments success metrics") {
    withMetricsMonitoringProcessor[MyWork, Unit](
      namespace = "namespace",
      shouldFail = false) {
      case (monitoringClient, monitoringProcessor) =>
    val worker = new MyWorker(
      monitoringProcessor,
      successful,
      messageToWorkShouldFail = false
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

  it("increments deterministic failure metric if transformation fails") {
    withMetricsMonitoringProcessor[MyWork, Unit](
      namespace = "namespace",
      shouldFail = false) {
      case (monitoringClient, monitoringProcessor) =>
    val worker = new MyWorker(
      monitoringProcessor,
      successful,
      messageToWorkShouldFail = true
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
      messageToWorkShouldFail = false
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
      messageToWorkShouldFail = false
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
      messageToWorkShouldFail = false
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
