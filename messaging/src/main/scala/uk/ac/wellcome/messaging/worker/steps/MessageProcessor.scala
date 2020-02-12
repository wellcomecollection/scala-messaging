package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.models.{DeterministicFailure, Result}

import scala.concurrent.{ExecutionContext, Future}

trait MessageProcessor[Message, Work, Summary] {
  type ResultSummary = Future[Result[Summary]]

  val transform: Message => Future[Work]
  val doWork: Work => ResultSummary

  final def process(message: Message)(
    implicit ec: ExecutionContext): ResultSummary = {

    val working = for {
      work <- transform(message)
      result <- doWork(work)
    } yield result

    working recover {
      case e => DeterministicFailure[Summary](e)
    }
  }
}
