package uk.ac.wellcome.messaging.sqsworker.alpakka

case class AlpakkaSQSWorkerConfig(
  namespace: String,
  queueUrl: String,
  parallelism: Int = 1
)
