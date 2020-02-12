package uk.ac.wellcome.messaging.worker

import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.worker.{MetricsFixtures, WorkerFixtures}
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import org.scalatest.prop.Tables._
import uk.ac.wellcome.messaging.worker.monitoring.metrics.MetricsMonitoringProcessor

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
        val worker = new MyWorker(
          successful,
          messageToWorkShouldFail = false
        )
    val monitoringClient = new FakeMetricsMonitoringClient(false)
    val process = worker.processMessage(message)(new MetricsMonitoringProcessor[MyMessage, _]("namespace")(monitoringClient))

        whenReady(process) { _ =>
          worker.callCounter.calledCount shouldBe 1

          assertMetricCount(
            monitoringClient, "namespace/Successful", 1,
          )

          assertMetricDurations(
            monitoringClient,
            "namespace/Duration",
            1)
        }
    }

  it("increments deterministic failure metric if transformation fails") {
        val worker = new MyWorker(
          successful,
          messageToWorkShouldFail = true
        )
    val monitoringClient = new FakeMetricsMonitoringClient(false)
    val process = worker.processMessage(message)(new MetricsMonitoringProcessor[MyMessage, _]("namespace")(monitoringClient))

        whenReady(process) { _ =>
          worker.callCounter.calledCount shouldBe 0

          assertMetricCount(
            monitoringClient, "namespace/DeterministicFailure", 1,
          )

          assertMetricDurations(
            monitoringClient,
            "namespace/Duration",
            1)
        }
    }

  it("doesn't increment metrics if monitoring fails") {
        val worker = new MyWorker(
          successful,
          messageToWorkShouldFail = false
        )
    val monitoringClient = new FakeMetricsMonitoringClient(true)
    val process = worker.processMessage(message)(new MetricsMonitoringProcessor[MyMessage, _]("namespace")(monitoringClient))

        whenReady(process) { _ =>
          worker.callCounter.calledCount shouldBe 1

          monitoringClient.incrementCountCalls shouldBe Map.empty

          monitoringClient.recordValueCalls shouldBe Map.empty
        }
    }

  it("increments deterministic failure metric if processing fails with deterministic failure") {
    val worker = new MyWorker(
      deterministicFailure,
      messageToWorkShouldFail = false
    )
    val monitoringClient = new FakeMetricsMonitoringClient(false)
    val process = worker.processMessage(message)(new MetricsMonitoringProcessor[MyMessage, _]("namespace")(monitoringClient))

    whenReady(process) { _ =>
      worker.callCounter.calledCount shouldBe 1

      assertMetricCount(
        monitoringClient, "namespace/DeterministicFailure", 1,
      )

      assertMetricDurations(
        monitoringClient,
        "namespace/Duration",
        1)
    }
  }

  it("increments non deterministic failure metric if processing fails with non deterministic failure") {
    val worker = new MyWorker(
      nonDeterministicFailure,
      messageToWorkShouldFail = false
    )
    val monitoringClient = new FakeMetricsMonitoringClient(false)
    val process = worker.processMessage(message)(new MetricsMonitoringProcessor[MyMessage, _]("namespace")(monitoringClient))

    whenReady(process) { _ =>
      worker.callCounter.calledCount shouldBe 1

      assertMetricCount(
        monitoringClient, "namespace/NonDeterministicFailure", 1,
      )

      assertMetricDurations(
        monitoringClient,
        "namespace/Duration",
        1)
    }
  }

}
