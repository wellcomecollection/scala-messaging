package uk.ac.wellcome.messaging.worker

import uk.ac.wellcome.messaging.worker.steps.{MessageDeserialiser, MessageSerialiser}

class SqsSerialiser[Value, InterServiceMonitoringContext] extends MessageSerialiser[Value, InterServiceMonitoringContext, Map[String, String]] {

}
