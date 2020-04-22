package uk.ac.wellcome.messaging.fixtures.monitoring.metrics

import grizzled.slf4j.Logging
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.worker.monitoring.metrics.{MetricsMonitoringClient, MetricsMonitoringProcessor}

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
        throw new RuntimeException("FakeMonitoringClient incrementCount Error!")
      }
      incrementCountCalls = incrementCountCalls + (metricName -> (incrementCountCalls
        .getOrElse(metricName, 0) + 1))
    }

    override def recordValue(metricName: String, value: Double): Future[Unit] =
      Future {
        info(s"MyMonitoringClient recordValue $metricName: $value")
        if (shouldFail) {
          throw new RuntimeException("FakeMonitoringClient recordValue Error!")
        }
        recordValueCalls = recordValueCalls + (metricName -> (recordValueCalls
          .getOrElse(metricName, List.empty) :+ value))
      }
  }

  def withFakeMonitoringClient[R](shouldFail: Boolean = false)(
    testWith: TestWith[FakeMetricsMonitoringClient, R])(
    implicit ec: ExecutionContext): R = {
    val fakeMonitoringClient = new FakeMetricsMonitoringClient(shouldFail)
    testWith(fakeMonitoringClient)
  }

  def withMetricsMonitoringProcessor[Work, R](namespace: String,
                                              shouldFail: Boolean = false)(
    testWith: TestWith[(FakeMetricsMonitoringClient,
                        MetricsMonitoringProcessor[Work]),
                       R])(implicit ec: ExecutionContext): R = {
    withFakeMonitoringClient(shouldFail) {
      client: FakeMetricsMonitoringClient =>
        val metricsProcessor =
          new MetricsMonitoringProcessor[Work](namespace)(client, ec)
        testWith((client, metricsProcessor))
    }
  }

  protected def assertMetricCount(metrics: FakeMetricsMonitoringClient,
                                  metricName: String,
                                  expectedCount: Int): Assertion =
    metrics.incrementCountCalls shouldBe Map(
      metricName -> expectedCount
    )

  protected def assertMetricDurations(metrics: FakeMetricsMonitoringClient,
                                      metricName: String,
                                      expectedNumberDurations: Int): Unit = {
    val durationMetric = metrics.recordValueCalls.get(
      metricName
    )

    durationMetric shouldBe defined
    durationMetric.get should have length expectedNumberDurations
    durationMetric.get.foreach(_ should be >= 0.0)

  }
}
