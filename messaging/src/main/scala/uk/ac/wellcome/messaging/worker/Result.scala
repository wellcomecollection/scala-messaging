package uk.ac.wellcome.messaging.worker

sealed trait Result[Summary] {
  val id: String
  val summary: Summary

  def pretty(resultType: String) =
    s"""
       |$resultType: $id
       |$summary
       """.stripMargin

}

case class DeterministicFailure[Summary](
                                          id: String,
                                          failure: Throwable,
                                          summary: Summary = None
                                        ) extends Result[Summary] with Completed {
  override def toString: String =
    pretty("DeterministicFailure")
}

case class NonDeterministicFailure[Summary](
                                             id: String,
                                             failure: Throwable,
                                             summary: Summary = None
                                           ) extends Result[Summary] with Retry {
  override def toString: String =
    pretty("NonDeterministicFailure")
}


case class Successful[Summary](
                                id: String,
                                summary: Summary = None
                              ) extends Result[Summary] with Completed  {
  override def toString: String =
    pretty("Successful")
}

case class PostProcessFailure[Summary](
                                        id: String,
                                        failure: Throwable,
                                        summary: Summary = None
                                      ) extends Result[Summary] with Completed {
  override def toString: String =
    pretty("PostProcessFailure")
}






