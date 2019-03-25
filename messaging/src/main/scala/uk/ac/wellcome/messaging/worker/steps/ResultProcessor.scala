package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.{ResultProcessorFailure, Result, Successful}

import scala.concurrent.{ExecutionContext, Future}

trait ResultProcessor[In, Out] {
  protected def processResult(result: Result[In]): Future[Out]

  protected def result(
                                             id: String)(
                                             result: Result[In]
                                           )(implicit ec: ExecutionContext): Future[Result[Out]] = {
    processResult(result).map(out =>
      Successful(id, Some(out))) recover {
        case e => ResultProcessorFailure(id, e)
      }
  }
}