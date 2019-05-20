package uk.ac.wellcome.messaging.typesafe

import com.amazonaws.services.sns.AmazonSNS
import com.typesafe.config.Config
import uk.ac.wellcome.config.models.AWSClientConfig
import uk.ac.wellcome.messaging.sns._
import uk.ac.wellcome.typesafe.config.builders.AWSClientConfigBuilder
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

  def buildSNSIndividualMessageSender(config: Config): SNSIndividualMessageSender =
    new SNSIndividualMessageSender(
      snsClient = buildSNSClient(config)
    )

  def buildSNSMessageWriter(config: Config): SNSMessageWriter =
    new SNSMessageWriter(snsClient = buildSNSClient(config))

  def buildSNSMessageSender(config: Config, namespace: String = "", subject: String): SNSMessageSender =
    new SNSMessageSender(
      snsClient = buildSNSClient(config),
      snsConfig =  buildSNSConfig(config, namespace = namespace),
      subject = subject
    )
}
