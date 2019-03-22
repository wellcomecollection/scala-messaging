package uk.ac.wellcome.messaging.worker

import scala.concurrent.{ExecutionContext, Future}

trait WorkerProcess[Work, Summary] {
  def run(in: Work)(implicit ec: ExecutionContext): Future[Result[Summary]]
}