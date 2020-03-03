package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.models.{DeterministicFailure, Result}

import scala.concurrent.{ExecutionContext, Future}

/***
  * Executes some operation on a [[Payload]] and returns a [[Result]] with a [[Value]]
  */
trait MessageProcessor[Payload, Value] {
  type ResultValue = Future[Result[Value]]

  protected val doWork: (Payload) => ResultValue

  final def process(work: Payload)(
    implicit ec: ExecutionContext): Future[Result[Value]] = {

    val working = for {
      result <- doWork(work)

    } yield result
    working recover {
      case e => DeterministicFailure[Value](e)
    }
  }

}
