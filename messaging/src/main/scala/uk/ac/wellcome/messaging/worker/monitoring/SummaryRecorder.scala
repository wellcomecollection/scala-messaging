package uk.ac.wellcome.messaging.worker.monitoring

import uk.ac.wellcome.messaging.worker.result.Result

import scala.concurrent.{ExecutionContext, Future}

trait SummaryRecorder {
  def record[ProcessResult <: Result[_]] (result: ProcessResult)(
    implicit ec: ExecutionContext
  ): Future[Unit]
}
