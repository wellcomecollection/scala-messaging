package uk.ac.wellcome.platform.archive.bagunpacker.config.builders

import com.typesafe.config.Config
import uk.ac.wellcome.messaging.sqsworker.alpakka.AlpakkaSQSWorkerConfig
import uk.ac.wellcome.messaging.typesafe.SQSBuilder.buildSQSConfig
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object AlpakkaSqsWorkerConfigBuilder {
  def build(config: Config) = {
    val sqsConfig = buildSQSConfig(config)
    val metricsNamespace = config.required[String]("aws.metrics.namespace")
    AlpakkaSQSWorkerConfig(
      metricsNamespace,
      sqsConfig.queueUrl,
      sqsConfig.parallelism
    )
  }
}
