package uk.ac.wellcome.messaging.fixtures.worker

import grizzled.slf4j.Logging
import org.scalatest.{Assertion, Matchers}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.worker.monitoring.metrics.MetricsMonitoringClient

import scala.concurrent.{ExecutionContext, Future}

trait MetricsFixtures extends Matchers {

  class FakeMetricsMonitoringClient(shouldFail: Boolean = false)(
    implicit ec: ExecutionContext)
    extends MetricsMonitoringClient
      with Logging {
    var incrementCountCalls: Map[String, Int] = Map.empty
    var recordValueCalls: Map[String, List[Double]] = Map.empty

    override def incrementCount(metricName: String): Future[Unit] = Future {
      info(s"MyMonitoringClient incrementing $metricName")
      if (shouldFail) {
        throw new RuntimeException(
          "FakeMonitoringClient incrementCount Error!")
      }
      incrementCountCalls = incrementCountCalls + (metricName -> (incrementCountCalls
        .getOrElse(metricName, 0) + 1))
    }

    override def recordValue(metricName: String, value: Double): Future[Unit] = Future {
      info(s"MyMonitoringClient recordValue $metricName: $value")
      if (shouldFail) {
        throw new RuntimeException("FakeMonitoringClient recordValue Error!")
      }
      recordValueCalls = recordValueCalls + (metricName -> (recordValueCalls
        .getOrElse(metricName, List.empty) :+ value))
    }
  }

  def withFakeMonitoringClient[R](shouldFail: Boolean = false)(testWith: TestWith[FakeMetricsMonitoringClient, R])(
    implicit ec: ExecutionContext): R = {
    val fakeMonitoringClient = new FakeMetricsMonitoringClient(shouldFail)
    testWith(fakeMonitoringClient)
  }

  protected def assertMetricCount(metrics: FakeMetricsMonitoringClient,
                                  metricName: String,
                                  expectedCount: Int,
                                  isEmpty: Boolean = false): Assertion =
    if (isEmpty) {
      metrics.incrementCountCalls shouldBe Map.empty
    } else {
      metrics.incrementCountCalls shouldBe Map(
        metricName -> expectedCount
      )
    }

  protected def assertMetricDurations(metrics: FakeMetricsMonitoringClient,
                                      metricName: String,
                                      expectedNumberDurations: Int,
                                      isEmpty: Boolean = false) = {
    val durationMetric = metrics.recordValueCalls.get(
      metricName
    )

    if (isEmpty) {
      durationMetric shouldNot be(defined)
    } else {
      durationMetric shouldBe defined
      durationMetric.get should have length expectedNumberDurations
      durationMetric.get.foreach(_ should be >= 0.0)
    }
  }
}
