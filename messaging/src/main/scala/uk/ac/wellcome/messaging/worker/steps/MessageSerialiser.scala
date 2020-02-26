package uk.ac.wellcome.messaging.worker.steps

import uk.ac.wellcome.messaging.worker.monitoring.tracing.MonitoringContextSerializerDeserialiser

/**
  * Deserialises a [[Message]] into a [[Payload]] and an optional [[InterServiceMonitoringContext]]
  */
trait MessageSerialiser[
  Value, InterServiceMonitoringContext, SerialisedMonitoringContext] {
  val monitoringSerialiser: MonitoringContextSerializerDeserialiser[
    InterServiceMonitoringContext,
    SerialisedMonitoringContext]

  final def apply(value: Value,
                  monitoringContext: InterServiceMonitoringContext)
    : (Either[Throwable, String],
       Either[Throwable, SerialisedMonitoringContext]) = ???
}
