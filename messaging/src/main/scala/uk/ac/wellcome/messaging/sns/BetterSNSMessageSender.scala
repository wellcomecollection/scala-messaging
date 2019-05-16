package uk.ac.wellcome.messaging.sns

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.PublishRequest
import uk.ac.wellcome.messaging.BetterMessageSender

import scala.util.Try

class BetterSNSMessageSender(
  snsClient: AmazonSNS,
  snsConfig: SNSConfig
) extends BetterMessageSender[SNSConfig] {
  override val defaultDestination: SNSConfig = snsConfig

  override def sendIndividualMessage(
    message: String,
    subject: String,
    destination: SNSConfig): Try[Unit] = Try {
    val request = new PublishRequest(destination.topicArn, message, subject)

    snsClient.publish(request)
  }
}
