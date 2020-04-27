package uk.ac.wellcome.messaging.sqs

import akka.actor.ActorSystem
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.alpakka.sqs.MessageAction.Delete
import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.{Done, NotUsed}
import grizzled.slf4j.Logging
import io.circe.Decoder
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.monitoring.Metrics

import scala.concurrent.Future

// Provides a stream for processing SQS messages.
//
// The main entry point is `foreach` -- callers should create an instance of
// this class, then pass the name of the stream and a processing function
// to foreach.  For example:
//
//      val sqsStream = SQSStream[NotificationMessage]
//
//      def processMessage(message: NotificationMessage): Future[Unit]
//
//      sqsStream.foreach(
//        streamName = "ExampleStream",
//        process = processMessage
//      )
//
class SQSStream[T](sqsClient: SqsAsyncClient,
                   sqsConfig: SQSConfig,
                   metricsSender: Metrics[Future, StandardUnit])(
  implicit val actorSystem: ActorSystem)
    extends Logging {

  implicit val dispatcher = actorSystem.dispatcher

  private val source: Source[Message, NotUsed] =
    SqsSource(sqsConfig.queueUrl)(sqsClient)
  private val sink: Sink[MessageAction, Future[Done]] =
    SqsAckSink(sqsConfig.queueUrl)(sqsClient)

  def foreach(streamName: String, process: T => Future[Unit])(
    implicit decoderT: Decoder[T]): Future[Done] =
    runStream(
      streamName = streamName,
      source =>
        source
          .mapAsyncUnordered(parallelism = sqsConfig.parallelism) {
            case (message, t) =>
              debug(s"Processing message ${message.messageId()}")
              process(t).map(_ => message)
        }
    )

  def runStream(
    streamName: String,
    modifySource: Source[(Message, T), NotUsed] => Source[Message, NotUsed])(
    implicit decoder: Decoder[T]): Future[Done] = {
    val metricName = s"${streamName}_ProcessMessage"

    implicit val materializer = Materializer(actorSystem)

    val src = modifySource(source.map { message =>
      (message, fromJson[T](message.body).get)
    })

    val srcWithLogging: Source[Delete, NotUsed] = src
      .map { m =>
        metricsSender.incrementCount(s"${metricName}_success")
        debug(s"Deleting message ${m.messageId()}")
        (MessageAction.Delete(m))
      }

    srcWithLogging
      .toMat(sink)(Keep.right)
      .withAttributes(ActorAttributes.supervisionStrategy(decider(metricName)))
      .run()
  }

  // Defines a "supervision strategy" -- this tells Akka how to react
  // if one of the elements fails.  We want to log the failing message,
  // then drop it and carry on processing the next message.
  //
  // https://doc.akka.io/docs/akka/2.5.6/scala/stream/stream-error.html#supervision-strategies
  //
  private def decider(metricName: String): Supervision.Decider = {
    case e @ (_: RecognisedFailure | _: JsonDecodingError) =>
      logException(e)
      metricsSender.incrementCount(s"${metricName}_recognisedFailure")
      Supervision.resume
    case e: Exception =>
      logException(e)
      metricsSender.incrementCount(s"${metricName}_failure")
      Supervision.Resume
    case _ => Supervision.Stop
  }

  private def logException(exception: Throwable): Unit = {
    exception match {
      case exception: RecognisedFailure =>
        logger.warn(s"Recognised failure: ${exception.getMessage}")
      case exception: JsonDecodingError =>
        logger.warn(s"JSON decoding error: ${exception.getMessage}")
      case exception: Exception =>
        logger.error(
          s"Unrecognised failure while: ${exception.getMessage}",
          exception)
    }
  }
}
