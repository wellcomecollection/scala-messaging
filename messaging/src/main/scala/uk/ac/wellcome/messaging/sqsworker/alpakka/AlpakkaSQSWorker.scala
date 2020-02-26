// package uk.ac.wellcome.messaging.sqsworker.alpakka

// import akka.actor.ActorSystem
// import akka.stream.alpakka.sqs
// import akka.stream.alpakka.sqs.MessageAction
// import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
// import com.amazonaws.services.sqs.AmazonSQSAsync
// import com.amazonaws.services.sqs.model.{Message => SQSMessage}
// import grizzled.slf4j.Logging
// import io.circe.Decoder
// import uk.ac.wellcome.messaging.MessageSender
// import uk.ac.wellcome.messaging.worker._
// import uk.ac.wellcome.messaging.worker.models._
// import uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor

// import scala.concurrent.{ExecutionContext, Future}

// /***
//  * Implementation of [[AkkaWorker]] that uses SQS as source and sink.
//  * It receives messages from SQS and deletes messages from SQS on successful completion
//  */
// class AlpakkaSQSWorker[Work,  InfraServiceMonitoringContext, InterServiceMonitoringContext, Summary, Destination, MessageAttributes](
//   config: AlpakkaSQSWorkerConfig,
//   val monitoringProcessorBuilder: (ExecutionContext) => MonitoringProcessor[Work,  InfraServiceMonitoringContext, InterServiceMonitoringContext],
//   val  messageSender: MessageSender[Destination, MessageAttributes]
// )(
//   val doWork: Work => Future[Result[Summary]]
// )(implicit
//   val as: ActorSystem,
//   val wd: Decoder[Work],
//   sc: AmazonSQSAsync,
// ) extends AkkaWorker[
//       SQSMessage,
//       Work,
//       InfraServiceMonitoringContext,
//       InterServiceMonitoringContext,
//       Summary,
//       MessageAction, Destination, MessageAttributes]
//     with SnsSqsDeserialiser[Work, InfraServiceMonitoringContext]
//     with Logging {

//   type SQSAction = SQSMessage => (SQSMessage, sqs.MessageAction)

//   val parallelism: Int = config.sqsConfig.parallelism
//   val source = SqsSource(config.sqsConfig.queueUrl)
//   val sink = SqsAckSink(config.sqsConfig.queueUrl)

//   private val makeVisibleAction = MessageAction
//     .changeMessageVisibility(visibilityTimeout = 0)

//   val retryAction: SQSAction = (message: SQSMessage) =>
//     (message, makeVisibleAction)

//   val completedAction: SQSAction = (message: SQSMessage) =>
//     (message, MessageAction.delete)
// }

// object AlpakkaSQSWorker {
//   def apply[Work, InfraServiceMonitoringContext, InterServiceMonitoringContext, Summary, Destination, MessageAttributes](
//                                                                                                        config: AlpakkaSQSWorkerConfig,
//                                                                                                        monitoringProcessorBuilder: (ExecutionContext) => MonitoringProcessor[Work, InfraServiceMonitoringContext, InterServiceMonitoringContext],
//                                                                                                      messageSender: MessageSender[Destination, MessageAttributes])(
//     process: Work => Future[Result[Summary]]
//   )(implicit
//     sc: AmazonSQSAsync,
//     as: ActorSystem,
//     wd: Decoder[Work]) =
//     new AlpakkaSQSWorker[Work, InfraServiceMonitoringContext, InterServiceMonitoringContext, Summary, Destination, MessageAttributes](
//       config, monitoringProcessorBuilder, messageSender
//     )(process)
// }
