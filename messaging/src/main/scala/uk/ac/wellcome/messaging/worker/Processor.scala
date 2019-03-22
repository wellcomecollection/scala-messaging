package uk.ac.wellcome.messaging.worker

import scala.concurrent.{ExecutionContext, Future}

trait Processor[Message, Work, Summary,
Operation <: BaseOperation[Work, Summary]] {
  protected def toWork(message: Message): Future[Work]
  protected val process: Operation

  def doProcess(id: String, message: Message)(implicit ec: ExecutionContext) = (
    for {
      work <- toWork(message)
      result <- process.run(work)
    } yield result ) recover {
    case e => DeterministicFailure(id, e, None)
  }
}