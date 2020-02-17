package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.models._

import scala.concurrent.{ExecutionContext, Future}

trait MonitoringProcessor[Work, Context] {
  implicit val ec: ExecutionContext

  def recordStart(work: Either[Throwable, Work],
                  context: Either[Throwable, Option[Context]]): Future[Context]

  def recordEnd[Recorded](work: Either[Throwable, Work],
                          context: Context,
                          result: Result[Recorded]): Future[Result[Unit]]
}
