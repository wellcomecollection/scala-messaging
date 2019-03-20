package uk.ac.wellcome.messaging.worker

import scala.concurrent.{ExecutionContext, Future}

trait SummaryRecorder {
  def record[ProcessResult <: Result[_]] (result: ProcessResult)(
    implicit ec: ExecutionContext
  ): Future[Unit]
}
