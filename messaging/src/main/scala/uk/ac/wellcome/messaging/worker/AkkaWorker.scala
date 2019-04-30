package uk.ac.wellcome.messaging.worker

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.concurrent.Future


trait AkkaWorker[Message, Work, Summary, Action]
  extends Worker[Message, Work, Summary, Action] {

  implicit val as: ActorSystem
  implicit val am: ActorMaterializer =
    ActorMaterializer(
      ActorMaterializerSettings(as)
    )
  implicit val ec = as.dispatcher

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

  def start: Future[Done] = completionSource(parallelism)
      .toMat(sink)(Keep.right)
      .run()
}

