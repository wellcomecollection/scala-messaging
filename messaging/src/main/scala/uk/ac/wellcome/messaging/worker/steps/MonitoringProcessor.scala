package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.models._

import scala.concurrent.{ExecutionContext, Future}

trait MonitoringProcessor[Message, Context] {

  def recordStart(message: Message)(
    implicit ec: ExecutionContext): Future[Context]

  def recordEnd[Recorded](message: Message,
                          context: Context,
                          result: Result[Recorded])(
    implicit ec: ExecutionContext): Future[Result[Unit]]
}
