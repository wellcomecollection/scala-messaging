package uk.ac.wellcome.messaging.sqs

import java.net.URI

import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.{SqsAsyncClient, SqsClient}

object SQSClientFactory {
  def createAsyncClient(region: String,
                        endpoint: String,
                        accessKey: String,
                        secretKey: String): SqsAsyncClient = {
    val standardClient = SqsAsyncClient.builder()
    if (endpoint.isEmpty)
      standardClient
        .region(Region.of(region))
        .build()
    else
      standardClient
        .credentialsProvider(
          StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)))
        .endpointOverride(new URI(endpoint))
        .build()
  }

  def createSyncClient(region: String,
                       endpoint: String,
                       accessKey: String,
                       secretKey: String): SqsClient = {
    val standardClient = SqsClient.builder()
    if (endpoint.isEmpty)
      standardClient
        .region(Region.of(region))
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
