package uk.ac.wellcome.messaging.worker.monitoring.metrics

import java.time.{Duration, Instant}

import uk.ac.wellcome.messaging.worker.models._

import scala.concurrent.{ExecutionContext, Future}

trait MetricsProcessor {

  val namespace: String

  private def metricName(name: String) = s"$namespace/$name"

  final def metric[ProcessMonitoringClient <: MetricsMonitoringClient](
    result: Result[_],
    startTime: Instant
  )(
    implicit monitoringClient: ProcessMonitoringClient,
    ec: ExecutionContext
  ): Future[Unit] = {
    val countResult = result match {
      case _: Successful[_] =>
        monitoringClient
          .incrementCount(metricName("Successful"))
      case _: DeterministicFailure[_] =>
        monitoringClient
          .incrementCount(metricName("DeterministicFailure"))
      case _: NonDeterministicFailure[_] =>
        monitoringClient
          .incrementCount(metricName("NonDeterministicFailure"))
      case _: MonitoringProcessorFailure[_] =>
        monitoringClient
          .incrementCount(metricName("MonitoringProcessorFailure"))
    }

    val recordDuration =
      monitoringClient.recordValue(
        metricName("Duration"),
        Duration
          .between(
            startTime,
            Instant.now()
          )
          .getSeconds
      )

    Future
      .sequence(
        List(countResult, recordDuration)
      )
      .map(_ => ())
  }
}
