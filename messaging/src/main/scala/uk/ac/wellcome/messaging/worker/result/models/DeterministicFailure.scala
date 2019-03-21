package uk.ac.wellcome.messaging.worker.result.models

import uk.ac.wellcome.messaging.worker.result.Completed

case class DeterministicFailure[Summary](
                                          id: String,
                                          failure: Throwable,
                                          summary: Summary = None
                                        ) extends Completed[Summary] {
                                          override def toString: String =
                                            pretty("DeterministicFailure")
                                        }
