package uk.ac.wellcome.messaging.worker.monitoring.metrics

import java.time.Instant

import uk.ac.wellcome.messaging.worker.models.{
  MonitoringProcessorFailure,
  Result,
  Successful
}
import uk.ac.wellcome.messaging.worker.steps.MonitoringProcessor

import scala.concurrent.{ExecutionContext, Future}

final class MetricsMonitoringProcessor[Payload](val namespace: String)(
  implicit val monitoringClient: MetricsMonitoringClient,
  val ec: ExecutionContext)
    extends MonitoringProcessor[Payload, Map[String, String], Instant]
    with MetricsProcessor {

  override def recordStart(
    work: Either[Throwable, (Payload, Map[String, String])])
    : Future[Either[Throwable, (Instant, Map[String, String])]] =
    Future.successful(Right((Instant.now, Map.empty)))

  override def recordEnd[Recorded](
    context: Either[Throwable, (Instant, Map[String, String])],
    result: Result[Recorded]
  ): Future[Result[Unit]] = {

    val monitoring = for {
      _: Unit <- metric(
        result,
        context
          .getOrElse(throw new Exception(s"context was Left: $context"))
          ._1)
    } yield Successful[Unit](())

    monitoring recover {
      case e => MonitoringProcessorFailure[Unit](e)
    }
  }
}
