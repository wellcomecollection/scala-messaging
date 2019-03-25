package uk.ac.wellcome.messaging.worker

sealed trait Result[Summary] {
  val id: String
  val summary: Option[Summary]

  def pretty(resultType: String) =
    s"""
       |$resultType: $id
       |$summary
       """.stripMargin
}

case class DeterministicFailure[Summary](
                                          id: String,
                                          failure: Throwable,
                                          summary: Option[Summary] = Option.empty[Summary]
                                        ) extends Result[Summary] with Completed {

  override def toString: String =
    pretty("DeterministicFailure")
}

case class NonDeterministicFailure[Summary](
                                             id: String,
                                             failure: Throwable,
                                             summary: Option[Summary] = Option.empty[Summary]
                                           ) extends Result[Summary] with Retry {
  override def toString: String =
    pretty("NonDeterministicFailure")
}


case class Successful[Summary](
                                id: String,
                                summary: Option[Summary] = Option.empty[Summary]
                              ) extends Result[Summary] with Completed  {
  override def toString: String =
    pretty("Successful")
}

case class ResultProcessorFailure[Summary](
                                        id: String,
                                        failure: Throwable,
                                        summary: Option[Summary] = Option.empty[Summary]
                                      ) extends Result[Summary] with Completed {
  override def toString: String =
    pretty("ResultProcessorFailure")
}

case class MonitoringProcessorFailure[Summary](
                                            id: String,
                                            failure: Throwable,
                                            summary: Option[Summary] = Option.empty[Summary]
                                          ) extends Result[Summary] with Completed {
  override def toString: String =
    pretty("MonitoringProcessorFailure")
}






