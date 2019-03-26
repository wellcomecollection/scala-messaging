package uk.ac.wellcome.messaging.worker.steps

import java.time.Instant

import uk.ac.wellcome.messaging.worker.logging.Logger
import uk.ac.wellcome.messaging.worker.models._
import uk.ac.wellcome.messaging.worker.monitoring._

import scala.concurrent.{ExecutionContext, Future}

trait MonitoringProcessor extends Monitoring with Logger {

  protected def record[Recorded, ProcessMonitoringClient <: MonitoringClient](
    startTime: Instant,
    result: Result[Recorded]
  )(implicit monitoringClient: ProcessMonitoringClient,
    ec: ExecutionContext): Future[Result[Unit]] = {

    val monitoring = for {
      _: Unit <- log(result)
      _: Unit <- metric(result, startTime)
    } yield Successful[Unit]()

    monitoring recover {
      case e => MonitoringProcessorFailure[Unit](e)
    }
  }
}
