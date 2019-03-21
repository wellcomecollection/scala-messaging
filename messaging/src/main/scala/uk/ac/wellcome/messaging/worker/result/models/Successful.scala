package uk.ac.wellcome.messaging.worker.result.models

import uk.ac.wellcome.messaging.worker.result.Completed

case class Successful[Summary](
                                id: String,
                                summary: Summary = None
                              ) extends Completed[Summary]  {
                                override def toString: String =
                                  pretty("Successful")
                              }
