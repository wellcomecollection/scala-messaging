import java.time.Instant
import java.util.UUID

import akka.actor.{ActorSystem, Cancellable}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}


trait Message[+MessageId] {
  val id: MessageId
}

case class Summary[ContextId](
                               id: ContextId,
                               duration: Duration,
                               description: String
                             )

sealed trait Result[
MessageId,
ContextId,
] {
  val messageId: MessageId
  val summary: Summary[ContextId]
}

trait Completed[MessageId, ContextId]
  extends Result[MessageId, ContextId]

trait Retry[MessageId, ContextId]
  extends Result[MessageId, ContextId]

case class Successful[MessageId, ContextId](
                                             messageId: MessageId,
                                             summary: Summary[ContextId]
                                           ) extends Completed[MessageId, ContextId]

case class DeterministicFailure[
MessageId,
ContextId,
FailureType
](
   messageId: MessageId,
   failure: FailureType,
   summary: Summary[ContextId]
 ) extends Completed[MessageId, ContextId]

case class NonDeterministicFailure[MessageId, ContextId, FailureType](
                                                                       messageId: MessageId,
                                                                       failure: FailureType,
                                                                       summary: Summary[ContextId]
                                                                     ) extends Retry[MessageId, ContextId]

trait Process[
MessageId,
ContextId
] {
  def run[Input](in: Input): Result[MessageId, ContextId]
}

trait MessageClient[MessageId] {
  type IdMessage <: Message[MessageId]

  def delete(id: MessageId): Future[MessageId]
  def retry(id: MessageId): Future[MessageId]
  def receive: Seq[IdMessage]
}

// reporting
// duration
// ?message -> IdentifiedMessage

trait Scheduling {
  val system: ActorSystem

  val startAt: FiniteDuration = Duration(1, "seconds")
  val interval: FiniteDuration

  val maxWait: Duration = Duration.Inf

  def schedule(f: Unit => Future[Unit]): Cancellable = {

    system.scheduler.schedule(startAt, interval) {
      println("hey")

      Await.result(f(), maxWait)
    }
  }
}

abstract class Worker[
MessageId,
ContextId,
MessagingClient <: MessageClient[MessageId],
MessageProcess <: Process[
  MessageId,
  ContextId]
] extends Scheduling {

  val process: MessageProcess
  val client: MessagingClient

  private def run[IdentifiedMessage <: Message[MessageId]](
                                                            message: IdentifiedMessage
                                                          ) =
    process.run(message) match {

      case o: Completed[MessageId, ContextId] =>
        client.delete(o.messageId)

      case o: Retry[MessageId, ContextId] =>
        client.retry(o.messageId)
    }

  def start: Cancellable = {
    schedule { _ =>
      println("scheduled")

      Future
        .sequence(client.receive.map(run))
        .map(_ => ())
    }
  }
}

case class MyMessage(
                      id: UUID,
                      body: String
                    ) extends Message[UUID]

class MyMessageClient() extends MessageClient[UUID] {
  override type IdMessage = MyMessage

  def delete(id: UUID): Future[UUID] = Future.successful {
    println("delete")

    id
  }
  def retry(id: UUID): Future[UUID] = Future.successful {
    println("retry")

    id
  }
  def receive: Seq[IdMessage] =
    List(
      MyMessage(UUID.randomUUID(), Instant.now.toString)
    )
}

class MyProcess()
  extends Process[UUID,UUID] {
  def run[MyMessage](in: MyMessage) = {
    println("processing")

    Successful(
      UUID.randomUUID(),
      Summary(
        UUID.randomUUID(),
        Duration(3, "seconds"),
        "successful"
      )
    )
  }
}

class MyWorker() extends Worker[
  UUID, UUID,
  MyMessageClient,
  MyProcess] {

  override val system = ActorSystem("my-actor-system")
  override val interval = Duration(10, "seconds")

  override val process = new MyProcess()
  override val client = new MyMessageClient()
}

// val myWorker = new MyWorker()
// myWorker.start

val s = new Scheduling {
  override val system = ActorSystem("my-actor-system")
  override val interval = Duration(1, "seconds")

  schedule(_ => Future.successful(()))
}

Thread.sleep(30000)