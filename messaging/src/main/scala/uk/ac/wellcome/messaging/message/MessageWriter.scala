package uk.ac.wellcome.messaging.message

import java.text.SimpleDateFormat
import java.util.Date

import com.amazonaws.services.sns.AmazonSNS
import grizzled.slf4j.Logging
import io.circe.Encoder
import uk.ac.wellcome.messaging.sns._
import uk.ac.wellcome.storage.s3.S3Config
import uk.ac.wellcome.storage.{KeyPrefix, ObjectStore}
import uk.ac.wellcome.json.JsonUtil._

import scala.concurrent.{ExecutionContext, Future}

case class MessageWriterConfig(
  snsConfig: SNSConfig,
  s3Config: S3Config
)

class MessageWriter[T](
  messageConfig: MessageWriterConfig,
  snsClient: AmazonSNS
)(implicit objectStore: ObjectStore[T], ec: ExecutionContext)
    extends Logging {

  val snsMessageSender = new BetterSNSMessageSender(
    snsClient = snsClient,
    snsConfig = messageConfig.snsConfig
  )

  private val dateFormat = new SimpleDateFormat("YYYY/MM/dd")

  private def getKeyPrefix(): String = {
    val topicName = messageConfig.snsConfig.topicArn.split(":").last
    val currentTime = new Date()
    s"$topicName/${dateFormat.format(currentTime)}/${currentTime.getTime.toString}"
  }

  def write(message: T, subject: String)(
    implicit encoder: Encoder[T]): Future[Unit] =
    for {
      jsonString <- Future.fromTry(toJson(message))
      encodedNotification <- Future.fromTry(
        toJson[MessageNotification](InlineNotification(jsonString))
      )

      // If the encoded message is less than 250KB, we can send it inline
      // in SNS/SQS (although the limit is 256KB, there's a bit of overhead
      // caused by the notification wrapper, so we're conservative).
      //
      // Max SQS message size:
      // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-limits.html#limits-messages
      //
      // Max SNS message size:
      // https://aws.amazon.com/sns/faqs/
      //
      notification: String <- if (encodedNotification
                                    .getBytes("UTF-8")
                                    .length > 250 * 1000) {
        createRemoteNotification(message)
      } else {
        Future.successful(encodedNotification)
      }

      _ <- Future.fromTry {
        snsMessageSender.send(notification, subject)
      }
    } yield ()

  private def createRemoteNotification(message: T): Future[String] =
    for {
      location <- objectStore.put(messageConfig.s3Config.bucketName)(
        message,
        keyPrefix = KeyPrefix(getKeyPrefix())
      )
      _ = info(s"Successfully stored message in location: $location")
      notification = RemoteNotification(location = location)
      jsonString <- Future.fromTry(toJson[MessageNotification](notification))
    } yield jsonString
}
