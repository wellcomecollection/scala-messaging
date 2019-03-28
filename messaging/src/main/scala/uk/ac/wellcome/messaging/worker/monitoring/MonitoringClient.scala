package uk.ac.wellcome.messaging.worker.monitoring

import scala.concurrent.Future

trait MonitoringClient {
  def incrementCount(metricName: String): Future[Unit]
  def recordValue(metricName: String, value: Double): Future[Unit]
}
