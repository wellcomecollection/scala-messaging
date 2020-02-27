package uk.ac.wellcome.messaging.worker

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.{Done, NotUsed}
import uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor

import scala.concurrent.{ExecutionContext, Future}

/**
  * Implementation of [[Worker]] based on akka streams
  */
trait AkkaWorker[Message,
                 Payload,
                 InfraServiceMonitoringContext,
                 InterServiceMonitoringContext,
                 Value,
                 Action,
  SerialisedMonitoringContext]
    extends Worker[
      Message,
      Payload,
      InfraServiceMonitoringContext,
      InterServiceMonitoringContext,
      Value,
      Action,
      SerialisedMonitoringContext] {

  implicit val as: ActorSystem
  implicit val am: ActorMaterializer =
    ActorMaterializer(
      ActorMaterializerSettings(as)
    )
  private val ec = as.dispatcher
  protected val monitoringProcessorBuilder: (
    ExecutionContext) => MonitoringProcessor[Payload,
                                             InfraServiceMonitoringContext,
                                             InterServiceMonitoringContext, SerialisedMonitoringContext]
  override final val monitoringProcessor = monitoringProcessorBuilder(ec)
  type MessageSource = Source[Message, NotUsed]
  type MessageSink = Sink[(Message, Action), Future[Done]]

  type ProcessedSource = Source[(Message, Action), NotUsed]

  protected val parallelism: Int

  protected val source: MessageSource
  protected val sink: MessageSink

  protected val retryAction: MessageAction
  protected val completedAction: MessageAction

  private def completionSource(parallelism: Int): ProcessedSource =
    source.mapAsyncUnordered(parallelism)(processMessage)

  def start: Future[Done] =
    completionSource(parallelism)
      .toMat(sink)(Keep.right)
      .run()
}
