package uk.ac.wellcome.messaging.worker.steps

/**
  * Deserialises a [[Message]] into a [[Payload]] and an optional [[InterServiceMonitoringContext]]
  */
trait MessageSerialiser[
  Value, InterServiceMonitoringContext, SerialisedMonitoringContext] {

  def serialise(value: Value): String

  final def apply(value: Value,
                  monitoringContext: InterServiceMonitoringContext)
    : (Either[Throwable, String],
       Either[Throwable, SerialisedMonitoringContext]) = ???
}
