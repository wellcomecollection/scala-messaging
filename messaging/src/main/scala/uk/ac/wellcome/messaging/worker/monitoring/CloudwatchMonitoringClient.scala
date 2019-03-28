package uk.ac.wellcome.messaging.worker.monitoring

import grizzled.slf4j.Logging
import uk.ac.wellcome.monitoring.MetricsSender

import scala.concurrent.Future

class CloudwatchMonitoringClient(metricsSender: MetricsSender)
    extends MonitoringClient
    with Logging {

  override def incrementCount(metricName: String): Future[Unit] = {
    metricsSender.incrementCount(metricName)
  }

  override def recordValue(metricName: String, value: Double): Future[Unit] = {
    metricsSender.recordValue(metricName, value)
  }
}
