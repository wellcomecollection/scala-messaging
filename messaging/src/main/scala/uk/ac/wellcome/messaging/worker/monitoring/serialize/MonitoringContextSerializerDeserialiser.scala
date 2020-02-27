package uk.ac.wellcome.messaging.worker.monitoring.serialize

import scala.util.Try

trait MonitoringContextSerialiser[InfraServiceMonitoringContext, SerializedMonitoringContext] {
  def serialise(monitoringContext: InfraServiceMonitoringContext): Try[SerializedMonitoringContext]

}
trait MonitoringContextDeserialiser[InfraServiceMonitoringContext, SerializedMonitoringContext] {

  def deserialise(t: Option[SerializedMonitoringContext]): Try[Option[InfraServiceMonitoringContext]]
}