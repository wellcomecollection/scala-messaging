package uk.ac.wellcome.messaging.worker.monitoring

import grizzled.slf4j.Logging
import uk.ac.wellcome.messaging.worker._

import scala.concurrent.{ExecutionContext, Future}

trait SummaryRecorder extends Logging {
  def record (result: Result[_])(implicit ec: ExecutionContext): Future[Unit] = Future {
    result match {
      case r: Successful[_] => info(r.toString)
      case r: NonDeterministicFailure[_] => warn(r.toString)
      case r: DeterministicFailure[_] => error(r.toString)
      case r: PostProcessFailure[_] => error(r.toString)
    }
  }
}