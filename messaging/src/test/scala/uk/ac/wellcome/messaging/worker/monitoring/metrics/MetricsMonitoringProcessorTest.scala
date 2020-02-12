package uk.ac.wellcome.messaging.worker.monitoring.metrics

import java.time.Instant

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.monitoring.metrics.MetricsFixtures
import uk.ac.wellcome.messaging.fixtures.worker.WorkerFixtures
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture

import scala.concurrent.ExecutionContext.Implicits._

class MetricsMonitoringProcessorTest
    extends FunSpec
    with Matchers
    with Akka
    with ScalaFutures
    with IntegrationPatience
    with WorkerFixtures
    with MetricsFixtures
    with MetricsSenderFixture {

  val successMetric = "namespace/Successful"
  val deterministicFailMetric = "namespace/DeterministicFailure"
  val nonDeterministicFailMetric = "namespace/NonDeterministicFailure"

  it("records a success metric") {

    withMetricsMonitoringProcessor[MyMessage, Unit](namespace = "namespace", shouldFail = false) { case (monitoringClient, processor) =>

      val recorded = processor.recordEnd(message, Instant.now, successful(work))

      whenReady(recorded) { action =>
        shouldBeSuccessful(action)

        assertMetricCount(metrics = monitoringClient, metricName = successMetric, expectedCount = 1)
        assertMetricDurations(
          metrics = monitoringClient,
          metricName = "namespace/Duration",
          expectedNumberDurations = 1)
      }
    }

  }

  it("reports monitoring failure if recording fails") {
    withMetricsMonitoringProcessor[MyMessage, Unit](namespace = "namespace", shouldFail = true) { case (monitoringClient, processor) =>


      val recorded = processor.recordEnd(message, Instant.now, successful(work))

      whenReady(recorded) { action =>
        shouldBeMonitoringProcessorFailure(action)

        monitoringClient.incrementCountCalls shouldBe Map.empty
        monitoringClient.recordValueCalls shouldBe Map.empty
      }
    }
  }


  it("records a deterministic failure") {
    withMetricsMonitoringProcessor[MyMessage, Unit](namespace = "namespace", shouldFail = false) { case (monitoringClient, processor) =>


      val recorded = processor.recordEnd(message, Instant.now, deterministicFailure(work))

      whenReady(recorded) { action =>
        shouldBeSuccessful(action)

        assertMetricCount(metrics = monitoringClient, metricName = deterministicFailMetric, expectedCount = 1)
        assertMetricDurations(
          metrics = monitoringClient,
          metricName = "namespace/Duration",
          expectedNumberDurations = 1)
      }

    }
  }

  it("records a non deterministic failure") {
    withMetricsMonitoringProcessor[MyMessage, Unit](namespace = "namespace", shouldFail = false) { case (monitoringClient, processor) =>


    val recorded = processor.recordEnd(message,Instant.now,nonDeterministicFailure(work))

    whenReady(recorded) { action =>
      shouldBeSuccessful(action)

      assertMetricCount(metrics = monitoringClient, metricName = nonDeterministicFailMetric, expectedCount = 1)
      assertMetricDurations(
        metrics = monitoringClient,
        metricName = "namespace/Duration",
        expectedNumberDurations = 1)
    }

  }
  }
}
