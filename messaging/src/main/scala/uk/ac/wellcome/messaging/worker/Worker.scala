package uk.ac.wellcome.messaging.worker

import java.time.Instant

import uk.ac.wellcome.messaging.worker.monitoring.MonitoringClient

import scala.concurrent.ExecutionContext

trait Worker[Message, Work, Summary, Operation <: BaseOperation[Work, Summary], ExternalMessageAction] extends Processor[Message, Work, Summary, Operation] with PostProcessor[ExternalMessageAction] {

  protected def processMessage[ProcessMonitoringClient <: MonitoringClient](
                                id: String,
                                message: Message
                              )(implicit
                                monitoringClient: ProcessMonitoringClient,
                                ec: ExecutionContext
                              )= {
    val startTime = Instant.now
    for {
      processResult <- doProcess(id, message)
      result <- doPostProcess(id, startTime, processResult)
      action <- toAction(result.asInstanceOf[Action])
    } yield (message, action)
  }
}
