package uk.ac.wellcome.messaging.sns

import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.ac.wellcome.messaging.{IndividualMessageSender, MessageSender}

import scala.util.Try

class SNSIndividualMessageSender(
  snsClient: SnsClient,
) extends IndividualMessageSender[SNSConfig] {
  override def send(message: String)(subject: String,
                                     destination: SNSConfig): Try[Unit] = Try {
    snsClient.publish(
      PublishRequest
        .builder()
        .message(message)
        .subject(subject)
        .topicArn(destination.topicArn)
        .build()
    )
  }
}

class SNSMessageSender(
  snsClient: SnsClient,
  snsConfig: SNSConfig,
  val subject: String
) extends MessageSender[SNSConfig] {
  override protected val underlying: IndividualMessageSender[SNSConfig] =
    new SNSIndividualMessageSender(
      snsClient = snsClient
    )

  override val destination: SNSConfig = snsConfig
}
