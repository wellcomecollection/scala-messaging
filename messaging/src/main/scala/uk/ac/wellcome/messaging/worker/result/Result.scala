package uk.ac.wellcome.messaging.worker.result

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







