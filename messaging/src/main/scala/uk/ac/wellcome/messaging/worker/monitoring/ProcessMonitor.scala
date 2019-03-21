package uk.ac.wellcome.messaging.worker.monitoring

import java.time.{Duration, Instant}

import uk.ac.wellcome.messaging.worker.result.Result
import uk.ac.wellcome.messaging.worker.result.models.{DeterministicFailure, NonDeterministicFailure, Successful}

import scala.concurrent.{ExecutionContext, Future}

trait ProcessMonitor[ProcessMonitoringClient <: MonitoringClient] {

  val namespace: String

  private def metricName(name: String) = s"$namespace/$name"

  def monitor[ProcessResult <: Result[_]](
                                           result: ProcessResult,
                                           startTime: Instant
                                         )(
                                           implicit monitoringClient: ProcessMonitoringClient,
                                           ec: ExecutionContext
                                         ): Future[List[Unit]] = {
    val countResult = result match {
      case _: Successful[_] => monitoringClient
        .incrementCount(metricName("Successful"))
      case _: DeterministicFailure[_] => monitoringClient
        .incrementCount(metricName("DeterministicFailure"))
      case _: NonDeterministicFailure[_] => monitoringClient
        .incrementCount(metricName("NonDeterministicFailure"))
    }

    val recordDuration =
      monitoringClient.recordValue(
        metricName("Duration"),
        Duration.between(
          startTime,
          Instant.now()
        ).getSeconds
      )

    Future.sequence(
      List(countResult, recordDuration)
    )
  }
}