package uk.ac.wellcome.messaging.memory

import io.circe.Encoder
import uk.ac.wellcome.messaging.BigMessageSender
import uk.ac.wellcome.storage.{ObjectStore, SerialisationStrategy}
import uk.ac.wellcome.storage.memory.MemoryObjectStore

class MemoryBigMessageSender[T](
  maxSize: Int = 100,
  storeNamespace: String = "MemoryBigMessageSender",
  destination: String = "MemoryBigMessageSender"
)(
  implicit
  val encoder: Encoder[T],
  serialisationStrategy: SerialisationStrategy[T]
) extends BigMessageSender[String, T] {
  override val messageSender: MemoryMessageSender = new MemoryMessageSender(
    destination = destination,
    subject = "Sent from MemoryBigMessageSender"
  )

  override val objectStore: ObjectStore[T] = new MemoryObjectStore[T]()
  override val namespace: String = storeNamespace
  override val maxMessageSize: Int = maxSize

  def messages: List[messageSender.underlying.MemoryMessage] = messageSender.messages
}
