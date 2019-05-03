package uk.ac.wellcome.messaging.sqsworker.alpakka

import uk.ac.wellcome.messaging.sqs.SQSConfig
import uk.ac.wellcome.monitoring.MetricsConfig

case class AlpakkaSQSWorkerConfig(
  metricsConfig: MetricsConfig,
  sqsConfig: SQSConfig
)
