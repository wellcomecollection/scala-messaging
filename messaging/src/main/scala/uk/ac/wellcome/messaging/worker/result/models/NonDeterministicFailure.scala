package uk.ac.wellcome.messaging.worker.result.models

import uk.ac.wellcome.messaging.worker.result.Retry

case class NonDeterministicFailure[Summary](
                                             id: String,
                                             failure: Throwable,
                                             summary: Summary = None
                                           ) extends Retry[Summary] {
                                             override def toString: String =
                                               pretty("NonDeterministicFailure")
                                           }
