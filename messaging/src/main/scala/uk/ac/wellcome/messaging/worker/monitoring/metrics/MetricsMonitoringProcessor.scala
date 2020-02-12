package uk.ac.wellcome.messaging.worker.monitoring.metrics

import java.time.Instant

import uk.ac.wellcome.messaging.worker.logging.Logger
import uk.ac.wellcome.messaging.worker.models.{
  MonitoringProcessorFailure,
  Result,
  Successful
}
import uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor

import scala.concurrent.{ExecutionContext, Future}

final class MetricsMonitoringProcessor[
  Message,
  ProcessMonitoringClient <: MetricsMonitoringClient](val namespace: String)(
  implicit val monitoringClient: ProcessMonitoringClient)
    extends MonitoringProcessor[Message, Instant]
    with Logger
    with MetricsProcessor {

  override def recordStart(message: Message)(
    implicit ec: ExecutionContext): Future[Instant] =
    Future.successful(Instant.now)

  override def recordEnd[Recorded](
    message: Message,
    context: Instant,
    result: Result[Recorded]
  )(implicit ec: ExecutionContext): Future[Result[Unit]] = {

    val monitoring = for {
      _: Unit <- log(result)
      _: Unit <- metric(result, context)
    } yield Successful[Unit]()

    monitoring recover {
      case e => MonitoringProcessorFailure[Unit](e)
    }
  }
}
