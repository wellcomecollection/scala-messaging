package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.models.{DeterministicFailure, Result}

import scala.concurrent.{ExecutionContext, Future}

/***
  * Executes some operation on a `Work` and returns a [[uk.ac.wellcome.messaging.worker.models.Result]]
 * with a optional descriptive `Summary`
  */
trait MessageProcessor[Work, Summary] {
  type ResultSummary = Future[Result[Summary]]

  protected val doWork: (Work) => ResultSummary

  final def process(workEither: Either[Throwable, Work])(
    implicit ec: ExecutionContext): Future[Result[Summary]] = workEither.fold(
    e => Future.successful(DeterministicFailure[Summary](e)),
    w => {

      val working = for {
        result <- doWork(w)

      } yield result
      working recover {
        case e => DeterministicFailure[Summary](e)
      }
    }
  )
}
