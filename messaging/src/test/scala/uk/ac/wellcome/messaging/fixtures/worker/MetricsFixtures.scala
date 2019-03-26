package uk.ac.wellcome.messaging.fixtures.worker

import grizzled.slf4j.Logging
import org.scalatest.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient

import scala.concurrent.{ExecutionContext, Future}

trait MetricsFixtures extends Matchers {

  class FakeMonitoringClient(shouldFail: Boolean = false)
    extends MonitoringClient
      with Logging {
    var incrementCountCalls: Map[String, Int] = Map.empty
    var recordValueCalls: Map[String, List[Double]] = Map.empty

    override def incrementCount(metricName: String)(
      implicit ec: ExecutionContext): Future[Unit] = Future {
      info(s"MyMonitoringClient incrementing $metricName")
      if (shouldFail) {
        throw new RuntimeException(
          "FakeMonitoringClient incrementCount Error!")
      }
      incrementCountCalls = incrementCountCalls + (metricName -> (incrementCountCalls
        .getOrElse(metricName, 0) + 1))
    }

    override def recordValue(metricName: String, value: Double)(
      implicit ec: ExecutionContext): Future[Unit] = Future {
      info(s"MyMonitoringClient recordValue $metricName: $value")
      if (shouldFail) {
        throw new RuntimeException("FakeMonitoringClient recordValue Error!")
      }
      recordValueCalls = recordValueCalls + (metricName -> (recordValueCalls
        .getOrElse(metricName, List.empty) :+ value))
    }
  }

  def withFakeMonitoringClient[R](shouldFail: Boolean = false)(testWith: TestWith[FakeMonitoringClient, R]): R = {
    val fakeMonitoringClient = new FakeMonitoringClient(shouldFail)
    testWith(fakeMonitoringClient)
  }

  protected def assertMetricCount(metrics: FakeMonitoringClient,
                                  metricName: String,
                                  expectedCount: Int,
                                  isEmpty: Boolean = false) = {

    if (isEmpty) {
      metrics.incrementCountCalls shouldBe Map.empty
    } else {
      metrics.incrementCountCalls shouldBe Map(
        metricName -> expectedCount
      )
    }
  }

  protected def assertMetricDurations(metrics: FakeMonitoringClient,
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
