package uk.ac.wellcome.messaging.worker.monitoring.metrics

import scala.concurrent.Future

trait MetricsMonitoringClient {
  def incrementCount(metricName: String): Future[Unit]
  def recordValue(metricName: String, value: Double): Future[Unit]
}
