package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.models.{DeterministicFailure, Result}

import scala.concurrent.{ExecutionContext, Future}

/***
  * Executes some operation on a [[Payload]] and returns a [[Result]] with a [[Value]]
  */
trait MessageProcessor[Payload, Value] {
  type ResultValue = Future[Result[Value]]

  protected val doWork: (Payload) => ResultValue

  final def process(workEither: Either[Throwable, Payload])(
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
