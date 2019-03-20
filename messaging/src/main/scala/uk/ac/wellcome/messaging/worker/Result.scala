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

trait Completed[Summary] extends Result[Summary]
trait Retry[Summary] extends Result[Summary]

case class PostProcessFailure[Summary](
                                        id: String,
                                        failure: Throwable,
                                        summary: Summary = None
                                      ) extends Completed[Summary]  {
  override def toString: String =
    pretty("PostProcessFailure")
}

case class Successful[Summary](
                                id: String,
                                summary: Summary = None
                              ) extends Completed[Summary]  {
  override def toString: String =
    pretty("Successful")
}

case class DeterministicFailure[Summary](
                                          id: String,
                                          failure: Throwable,
                                          summary: Summary = None
                                        ) extends Completed[Summary] {
  override def toString: String =
    pretty("DeterministicFailure")
}

case class NonDeterministicFailure[Summary](
                                             id: String,
                                             failure: Throwable,
                                             summary: Summary = None
                                           ) extends Retry[Summary] {
  override def toString: String =
    pretty("NonDeterministicFailure")
}