package uk.ac.wellcome.messaging.typesafe

import com.amazonaws.services.sns.AmazonSNS
import com.typesafe.config.Config
import uk.ac.wellcome.config.models.AWSClientConfig
import uk.ac.wellcome.messaging.sns._
import uk.ac.wellcome.typesafe.config.builders.{AWSClientConfigBuilder, AkkaBuilder}
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object SNSBuilder extends AWSClientConfigBuilder {
  def buildSNSConfig(config: Config, namespace: String = ""): SNSConfig = {
    val topicArn = config
      .required[String](s"aws.$namespace.sns.topic.arn")

    SNSConfig(topicArn = topicArn)
  }

  private def buildSNSClient(awsClientConfig: AWSClientConfig): AmazonSNS =
    SNSClientFactory.create(
      region = awsClientConfig.region,
      endpoint = awsClientConfig.endpoint.getOrElse(""),
      accessKey = awsClientConfig.accessKey.getOrElse(""),
      secretKey = awsClientConfig.secretKey.getOrElse("")
    )

  def buildSNSClient(config: Config): AmazonSNS =
    buildSNSClient(
      awsClientConfig = buildAWSClientConfig(config, namespace = "sns")
    )

  def buildSNSMessageWriter(config: Config): SNSMessageWriter =
    new SNSMessageWriter(snsClient = buildSNSClient(config))(
      ec = AkkaBuilder.buildExecutionContext())

  def buildSNSWriter(config: Config, namespace: String = ""): SNSWriter =
    new SNSWriter(
      snsMessageWriter = buildSNSMessageWriter(config),
      snsConfig = buildSNSConfig(config, namespace = namespace)
    )

  def buildBetterSNSMessageSender(config: Config, namespace: String = ""): BetterSNSMessageSender =
    new BetterSNSMessageSender(
      snsClient = buildSNSClient(config),
      snsConfig = buildSNSConfig(config, namespace = namespace)
    )
}
