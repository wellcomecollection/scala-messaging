package uk.ac.wellcome.messaging.typesafe

import com.typesafe.config.Config
import uk.ac.wellcome.messaging.sqsworker.alpakka.AlpakkaSQSWorkerConfig
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.monitoring.typesafe.MetricsBuilder

object AlpakkaSqsWorkerConfigBuilder {
  def build(config: Config) =
    AlpakkaSQSWorkerConfig(
      metricsConfig = MetricsBuilder.buildMetricsConfig(config),
      sqsConfig = SQSBuilder.buildSQSConfig(config)
    )
}
