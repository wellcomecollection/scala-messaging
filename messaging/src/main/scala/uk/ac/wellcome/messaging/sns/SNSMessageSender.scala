package uk.ac.wellcome.messaging.sns

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.PublishRequest
import uk.ac.wellcome.messaging.{IndividualMessageSender, MessageSender}

import scala.util.Try

class SNSIndividualMessageSender(
  snsClient: AmazonSNS,
) extends IndividualMessageSender[SNSConfig] {
  override def send(message: String)(subject: String,
                                     destination: SNSConfig): Try[Unit] = Try {
    snsClient.publish(
      new PublishRequest()
        .withMessage(message)
        .withSubject(subject)
        .withTopicArn(destination.topicArn)
    )
  }
}

class SNSMessageSender(
  snsClient: AmazonSNS,
  snsConfig: SNSConfig,
  val subject: String
) extends MessageSender[SNSConfig] {
  override protected val underlying: IndividualMessageSender[SNSConfig] =
    new SNSIndividualMessageSender(
      snsClient = snsClient
    )

  override protected val destination: SNSConfig = snsConfig
}
