package uk.ac.wellcome.messaging.typesafe

import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.messaging.worker.monitoring.metrics.cloudwatch.CloudwatchMetricsMonitoringClient
import uk.ac.wellcome.monitoring.typesafe.MetricsBuilder

import scala.concurrent.ExecutionContext

object CloudwatchMonitoringClientBuilder {
  def buildCloudwatchMonitoringClient(config: Config)(
    implicit
    materializer: ActorMaterializer,
    ec: ExecutionContext): CloudwatchMetricsMonitoringClient =
    new CloudwatchMetricsMonitoringClient(MetricsBuilder.buildMetricsSender(config))
}
