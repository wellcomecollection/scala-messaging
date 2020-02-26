package uk.ac.wellcome.messaging.worker

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.monitoring.metrics.MetricsFixtures
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
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
        val messageSender = new MemoryMessageSender()
        val worker = new MyWorker(
          monitoringProcessor,
          messageSender,
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
          messageSender.getMessages[String] shouldBe List("Summary Successful")
        }
    }
  }

  it("increments deterministic failure metric if transformation returns a Left") {
    withMetricsMonitoringProcessor[MyWork, Unit](
      namespace = "namespace",
      shouldFail = false) {
      case (monitoringClient, monitoringProcessor) =>
        val messageSender = new MemoryMessageSender()
        val worker = new MyWorker(
          monitoringProcessor,
          messageSender,
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
          messageSender.getMessages[String] shouldBe List()
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
        val messageSender = new MemoryMessageSender()
        val worker = new MyWorker(
          monitoringProcessor,
          new MemoryMessageSender(),
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
          messageSender.getMessages[String] shouldBe List()
        }
    }
  }

  it("doesn't increment metrics if monitoring fails") {
    withMetricsMonitoringProcessor[MyWork, Assertion](
      namespace = "namespace",
      shouldFail = true) {
      case (monitoringClient, monitoringProcessor) =>
        val messageSender = new MemoryMessageSender()
        val worker = new MyWorker(
          monitoringProcessor,
          messageSender,
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
        val messageSender = new MemoryMessageSender()
        val worker = new MyWorker(
          monitoringProcessor,
          messageSender,
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
          messageSender.getMessages[String] shouldBe List()
        }
    }
  }

  it(
    "increments non deterministic failure metric if processing fails with non deterministic failure") {
    withMetricsMonitoringProcessor[MyWork, Unit](
      namespace = "namespace",
      shouldFail = false) {
      case (monitoringClient, monitoringProcessor) =>
        val messageSender = new MemoryMessageSender()
        val worker = new MyWorker(
          monitoringProcessor,
          messageSender,
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
          messageSender.getMessages[String] shouldBe List()
        }
    }
  }

}
