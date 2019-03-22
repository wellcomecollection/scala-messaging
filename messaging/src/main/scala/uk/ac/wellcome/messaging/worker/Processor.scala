package uk.ac.wellcome.messaging.worker

import scala.concurrent.{ExecutionContext, Future}

trait Processor[Message, Work, Summary,
Operation <: BaseOperation[Work, Summary]] {
  protected def toWork(message: Message): Future[Work]
  protected val process: Operation

  def doProcess(id: String, message: Message)(implicit ec: ExecutionContext) = {

    val result: Future[Result[_]] = for {
      work <- toWork(message)
      result <- process.run(work)
    } yield result

    val recoveredResult = result.recover {
      case e => DeterministicFailure(id, e, None)
    }

    recoveredResult
  }
}