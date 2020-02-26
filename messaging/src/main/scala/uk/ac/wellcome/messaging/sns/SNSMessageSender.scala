package uk.ac.wellcome.messaging.sns

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.PublishRequest
import uk.ac.wellcome.messaging.{IndividualMessageSender, MessageSender}

import scala.util.Try

class SNSIndividualMessageSender(
  snsClient: AmazonSNS,
) extends IndividualMessageSender[SNSConfig, Map[String, String]] {
  override def send(message: String, attributes: Option[Map[String, String]])(subject: String,
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
) extends MessageSender[Map[String, String]] {

  type Destination = SNSConfig

  override protected val underlying: IndividualMessageSender[SNSConfig, Map[String, String]] =
    new SNSIndividualMessageSender(
      snsClient = snsClient
    )

  override val destination: SNSConfig = snsConfig
}
