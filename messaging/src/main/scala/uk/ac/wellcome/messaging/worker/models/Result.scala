package uk.ac.wellcome.messaging.worker.models

sealed trait Result[Summary] {
  val summary: Option[Summary]
  def pretty(resultType: String) =
    s"${this.getClass.getName}: $summary"
}

case class DeterministicFailure[Summary](
  failure: Throwable,
  summary: Option[Summary] = Option.empty[Summary]
) extends Result[Summary] with Completed

case class NonDeterministicFailure[Summary](
  failure: Throwable,
  summary: Option[Summary] = Option.empty[Summary]
) extends Result[Summary] with Retry

case class Successful[Summary](
  summary: Option[Summary] = Option.empty[Summary]
) extends Result[Summary] with Completed

case class MonitoringProcessorFailure[Summary](
  failure: Throwable,
  summary: Option[Summary] = Option.empty[Summary]
) extends Result[Summary] with Completed
