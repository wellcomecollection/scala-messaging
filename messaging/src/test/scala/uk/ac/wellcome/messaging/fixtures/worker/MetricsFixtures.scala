package uk.ac.wellcome.messaging.fixtures.worker

import org.scalatest.Matchers

trait MetricsFixtures extends WorkerFixtures with Matchers {
  protected def assertMetricCount(metrics: MyMonitoringClient, metricName : String, expectedCount : Int, isEmpty: Boolean = false) = {

    if(isEmpty) {
      metrics.incrementCountCalls shouldBe Map.empty
    } else {
      metrics.incrementCountCalls shouldBe Map(
        metricName -> expectedCount
      )
    }
  }

  protected def assertMetricDurations(metrics: MyMonitoringClient, metricName: String, expectedNumberDurations: Int, isEmpty: Boolean = false) = {
    val durationMetric = metrics.recordValueCalls.get(
      metricName
    )

    if(isEmpty) {
      durationMetric shouldNot be(defined)
    } else {
      durationMetric shouldBe defined
      durationMetric.get should have length expectedNumberDurations
      durationMetric.get.foreach(_ should be >= 0.0)
    }
  }
}
