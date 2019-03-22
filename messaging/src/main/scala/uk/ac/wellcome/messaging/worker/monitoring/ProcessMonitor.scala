package uk.ac.wellcome.messaging.worker.monitoring

import java.time.{Duration, Instant}

import uk.ac.wellcome.messaging.worker._

import scala.concurrent.{ExecutionContext, Future}

trait ProcessMonitor {

  val namespace: String

  private def metricName(name: String) = s"$namespace/$name"

  def monitor[ProcessMonitoringClient <: MonitoringClient](
                                           result: Result[_],
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
      case _: PostProcessFailure[_] => monitoringClient
        .incrementCount(metricName("PostProcessFailure"))
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