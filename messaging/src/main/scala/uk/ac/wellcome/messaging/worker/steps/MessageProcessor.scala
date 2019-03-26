package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.DeterministicFailure
import uk.ac.wellcome.messaging.worker.models.{DeterministicFailure, Result}

import scala.concurrent.{ExecutionContext, Future}

trait MessageProcessor[Message, Work, Summary] {
  protected def transform(message: Message): Future[Work]
  protected def processMessage(work: Work): Future[Result[Summary]]

  protected def process(id: String)(message: Message)(
    implicit ec: ExecutionContext): Future[Result[Summary]] = {
    val working = for {
      work <- transform(message)
      result <- processMessage(work)
    } yield result

    working recover {
      case e => DeterministicFailure[Summary](id, e)
    }
  }
}
