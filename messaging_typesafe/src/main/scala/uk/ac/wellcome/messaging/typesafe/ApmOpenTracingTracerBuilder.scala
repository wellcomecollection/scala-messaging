package uk.ac.wellcome.messaging.typesafe
import co.elastic.apm.attach.ElasticApmAttacher
import co.elastic.apm.opentracing.ElasticApmTracer
import com.typesafe.config.Config
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.collection.JavaConverters._

object ApmOpenTracingTracerBuilder{
  def buildCloudwatchMonitoringClient(config: Config): ElasticApmTracer = {
    ElasticApmAttacher.attach(
      Map(
        "application_packages" -> "uk.ac.wellcome",
        "service_name" ->  config.required[String](s"apm.tracing.service"),
        "server_urls" -> config.required[String](s"apm.tracing.url"),
        "secret_token" -> config.required[String](s"apm.tracing.secret_token")
      ).asJava)

    new ElasticApmTracer()
  }

}
