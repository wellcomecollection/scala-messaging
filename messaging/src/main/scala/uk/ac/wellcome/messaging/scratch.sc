import java.time.Instant
import java.util.UUID

import scala.util.{Success, Try}

trait Message[Id] {
  val id: Id
}

trait PublishResult[Id] {
  val id: Id
}

trait Publisher[Id, M <: Message[Id], R <: PublishResult[Id]] {
  def submit(m: Seq[M]): Seq[Try[R]]
}

trait Publish[
  Id,
  M <: Message[Id],
  R <: PublishResult[Id],
  P <: Publisher[Id, M, R]
] {
  def apply(m: Seq[M])(implicit publisher: P): Seq[Try[R]]
}

// ---

// Message
case class MyMessage(
                      id: UUID,
                      subject: String,
                      body: String
                    ) extends Message[UUID]

// Publication
case class MyPublishResult(id: UUID, publishedAt: Instant)
  extends PublishResult[UUID]

class MyPublisher extends Publisher[UUID, MyMessage, MyPublishResult] {
  def submit(myMessages: Seq[MyMessage]): Seq[Try[MyPublishResult]] = {
    myMessages.map { message =>

      println(message)

      Success(
        MyPublishResult(message.id, Instant.now())
      )
    }
  }
}

val myMessages: Seq[MyMessage] = Seq(
  MyMessage(UUID.randomUUID(), "subject", "body")
)

val myPublisher: MyPublisher = new MyPublisher()

myPublisher.submit(myMessages)