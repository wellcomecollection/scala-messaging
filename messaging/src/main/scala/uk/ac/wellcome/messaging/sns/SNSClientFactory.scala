package uk.ac.wellcome.messaging.sns

import java.net.URI

import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient

object SNSClientFactory {
  def create(region: String,
             endpoint: String,
             accessKey: String,
             secretKey: String): SnsClient = {
    val standardClient = SnsClient.builder().region(Region.of(region))
    if (endpoint.isEmpty)
      standardClient
        .build()
    else
      standardClient
        .credentialsProvider(
          StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)))
        .endpointOverride(new URI(endpoint))
        .build()
  }
}
