package uk.ac.wellcome.messaging.worker.logging

import grizzled.slf4j.Logging
import uk.ac.wellcome.messaging.worker.models._

import scala.concurrent.{ExecutionContext, Future}

trait Logger extends Logging {
  def log[T](result: Result[T])(implicit ec: ExecutionContext): Future[Unit] =
    Future {
      result match {
        case r @ Successful(summary) => info(r.pretty)
        case r @ NonDeterministicFailure(e, summary) => error(r.pretty, e)
        case r @ DeterministicFailure(e, summary) => error(r.toString, e)
        case r @ MonitoringProcessorFailure(e, summary) => error(r.toString, e)
      }
    }
}
