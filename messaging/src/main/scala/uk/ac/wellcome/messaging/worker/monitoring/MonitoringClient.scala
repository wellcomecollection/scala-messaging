package uk.ac.wellcome.messaging.worker.monitoring

import scala.concurrent.{ExecutionContext, Future}

trait MonitoringClient {
  def incrementCount(metricName: String)(implicit ec: ExecutionContext): Future[Unit]
  def recordValue(metricName: String, value: Double)(implicit ec: ExecutionContext): Future[Unit]
}
