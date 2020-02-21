package uk.ac.wellcome.messaging.worker.models

sealed trait Result[Value]

case class Successful[Value](
                                value: Value
                              ) extends Result[Value]
  with Completed {
  def pretty =
    s"${this.getClass.getSimpleName}: $value"
}

sealed trait FailedResult[Value] extends Result[Value] {
  val failure: Throwable
  def pretty =
    s"${this.getClass.getSimpleName}"
}


case class DeterministicFailure[Value](
                                        failure: Throwable
) extends FailedResult[Value]
    with Completed

case class NonDeterministicFailure[Value](
                                           failure: Throwable
) extends FailedResult[Value]
    with Retry



case class MonitoringProcessorFailure[Value](
                                              failure: Throwable
) extends FailedResult[Value]
    with Completed
