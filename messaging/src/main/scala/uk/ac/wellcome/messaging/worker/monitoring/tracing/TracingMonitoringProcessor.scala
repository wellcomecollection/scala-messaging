package uk.ac.wellcome.messaging.worker.monitoring.tracing

import uk.ac.wellcome.messaging.worker.models.Result
import uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor

import scala.concurrent.{ExecutionContext, Future}

class TracingMonitoringProcessor[Message, Context]
    extends MonitoringProcessor[Message, Context] {
  override def recordStart(work: Either[Throwable, Message], context: Either[Throwable, Option[Context]])(implicit ec: ExecutionContext): Future[Context] = ???

  override def recordEnd[Recorded](work: Either[Throwable, Message], context: Context, result: Result[Recorded])(implicit ec: ExecutionContext): Future[Result[Unit]] = ???
}
