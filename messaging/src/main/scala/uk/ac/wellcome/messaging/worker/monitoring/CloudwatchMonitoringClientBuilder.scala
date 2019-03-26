package uk.ac.wellcome.messaging.worker.monitoring

import uk.ac.wellcome.monitoring.typesafe.MetricsBuilder

import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

object CloudwatchMonitoringClientBuilder {
  def buildCloudwatchMonitoringClient(config: Config)(
    implicit
    materializer: ActorMaterializer,
    ec: ExecutionContext) = {
    new CloudwatchMonitoringClient(MetricsBuilder.buildMetricsSender(config))
  }
}
