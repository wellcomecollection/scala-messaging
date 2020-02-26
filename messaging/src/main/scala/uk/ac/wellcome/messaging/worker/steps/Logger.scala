package uk.ac.wellcome.messaging.worker.steps

import grizzled.slf4j.Logging
import uk.ac.wellcome.messaging.worker.models._

import scala.concurrent.{ExecutionContext, Future}

trait Logger extends Logging {
  def log(result: Result[_])(implicit ec: ExecutionContext): Future[Unit] =
    Future {
      result match {
        case r @ Successful(_)                    => info(r.pretty)
        case r @ NonDeterministicFailure(e, _)    => warn(r.pretty, e)
        case r @ DeterministicFailure(e, _)       => error(r.toString, e)
        case r @ MonitoringProcessorFailure(e, _) => error(r.toString, e)
      }
    }
}
