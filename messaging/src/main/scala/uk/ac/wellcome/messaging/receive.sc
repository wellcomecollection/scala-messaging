import java.util.UUID

import akka.actor.ActorSystem

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.ExecutionContext.Implicits.global


trait Message[Id] {
  val id: Id
}

trait Receiver[Id, M <: Message[Id]] {
  def receive(): Seq[M]
}

trait Receive[
  Id,
  M <: Message[Id],
] {
  def apply(r: Receiver[Id,M]): Seq[M]
}

// --

// Message
case class MyMessage(
                      id: UUID,
                      subject: String,
                      body: String
                    ) extends Message[UUID]

// Receiver
class MyReceiver extends Receiver[UUID, MyMessage] {
  override def receive(): Seq[MyMessage] = {

    val received = List(
      MyMessage(UUID.randomUUID(), "subject", "body")
    )

    Thread.sleep(10000L)

    received
  }
}

// ---

trait MessageQueue[Id, M <: Message[Id], R <: Receiver[Id, M]] {
  val receiver: R

  def delete(id: Id): Future[Unit]
  def refresh(id: Id, duration: Duration): Future[Unit]
  def receive(): Seq[M] = receiver.receive()
}

trait MessageStore[Id, M <: Message[Id]] {
  val messages: TrieMap[Id, M]

  def add(m: M) = messages.update(m.id, m)
  def delete(id: Id) = messages.remove(id)
  def get(id: Id) = messages.get(id: Id)
}

trait MessageService[
    Id,
    M <: Message[Id],
    R <: Receiver[Id, M],
    Queue <: MessageQueue[Id, M, R],
    Store <:  MessageStore[Id, M]
  ] {

  protected val actorSystem: ActorSystem

  lazy val executor = actorSystem.dispatcher
  lazy val scheduler = actorSystem.scheduler

  protected val queue: Queue
  protected val store: Store

  val defaultDuration: FiniteDuration =
    Duration(1, "seconds")

  def startRefresh = {
    scheduler.schedule(
      initialDelay = FiniteDuration(0, "seconds"),
      interval = defaultDuration,
      runnable = new Runnable {
        def run() {
          store.messages.foreach {
            case (id, _) => queue.refresh(id, defaultDuration)
          }
        }
      }
    )(executor)
  }

  def receive: Seq[M] = {
    val messages = queue.receive
    messages.foreach(store.add)
    messages
  }

  def delete(ids: Seq[Id]) = {
    ids.map { id =>
      queue.delete(id)
        .map(_ => store.delete(id))
        .map(_ => id)
    }
  }
}

// Queue

class MyMessageQueue(r: MyReceiver) extends MessageQueue[UUID, MyMessage, MyReceiver] {

  override val receiver: MyReceiver = r

  def delete(id: UUID) = Future.successful {
    println(s"deleted $id")
  }

  def refresh(id: UUID, duration: Duration)= Future.successful {
    println(s"refreshed $id")
  }
}

// Store

class MyMessageStore extends MessageStore[UUID, MyMessage] {
  val messages = new TrieMap[UUID, MyMessage]()
}

// Service

class MyMessageService() extends MessageService[UUID, MyMessage, MyReceiver, MyMessageQueue, MyMessageStore] {

  private val receiver = new MyReceiver()

  override protected val actorSystem: ActorSystem = ActorSystem("my-actor-system")
  override protected val queue: MyMessageQueue = new MyMessageQueue(receiver)
  override protected val store: MyMessageStore = new MyMessageStore()

  val cancellableRefresh = startRefresh
}

val myMessageService = new MyMessageService()
val messages = myMessageService.receive

println(messages)

Thread.sleep(30000L)

myMessageService.delete(messages.map(_.id))
