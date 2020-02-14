package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.models._

import scala.concurrent.{ExecutionContext, Future}

trait MonitoringProcessor[Work, Context] {

  def recordStart(work: Either[Throwable, Work], context: Either[Throwable, Option[Context]])(
    implicit ec: ExecutionContext): Future[Context]

  def recordEnd[Recorded](work: Either[Throwable, Work],
                          context: Context,
                          result: Result[Recorded])(
    implicit ec: ExecutionContext): Future[Result[Unit]]
}
