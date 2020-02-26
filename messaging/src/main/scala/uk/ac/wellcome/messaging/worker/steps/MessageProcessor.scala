package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.models.{DeterministicFailure, Result}

import scala.concurrent.{ExecutionContext, Future}

/***
  * Executes some operation on a [[Work]] and returns a [[Result]] with a [[Value]]
  */
trait MessageProcessor[Work, Value] {
  type ResultValue = Future[Result[Value]]

  protected val doWork: (Work) => ResultValue

  final def process(workEither: Either[Throwable, Work])(
    implicit ec: ExecutionContext): Future[Result[Value]] = workEither.fold(
    e => Future.successful(DeterministicFailure[Value](e)),
    w => {

      val working = for {
        result <- doWork(w)

      } yield result
      working recover {
        case e => DeterministicFailure[Value](e)
      }
    }
  )
}
