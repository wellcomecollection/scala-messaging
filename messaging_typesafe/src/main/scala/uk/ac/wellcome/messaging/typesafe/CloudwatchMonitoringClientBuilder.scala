package uk.ac.wellcome.messaging.typesafe

import akka.stream.Materializer
import com.typesafe.config.Config
import uk.ac.wellcome.messaging.worker.monitoring.metrics.cloudwatch.CloudwatchMetricsMonitoringClient
import uk.ac.wellcome.monitoring.typesafe.CloudWatchBuilder

import scala.concurrent.ExecutionContext

object CloudwatchMonitoringClientBuilder {
  def buildCloudwatchMonitoringClient(config: Config)(
    implicit
    materializer: Materializer,
    ec: ExecutionContext): CloudwatchMetricsMonitoringClient =
    new CloudwatchMetricsMonitoringClient(
      CloudWatchBuilder.buildCloudWatchMetrics(config))
}
