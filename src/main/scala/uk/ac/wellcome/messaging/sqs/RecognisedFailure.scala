package uk.ac.wellcome.messaging.sqs

trait RecognisedFailure extends Exception { self: Throwable =>
  val message: String = self.getMessage
}
