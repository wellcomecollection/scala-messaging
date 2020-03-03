package uk.ac.wellcome.messaging.fixtures.monitoring.tracing

import io.opentracing.mock.MockSpan
import io.opentracing.{Span, Tracer}
import org.scalatest.{Matchers, Suite}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.worker.monitoring.tracing.OpenTracingMonitoringProcessor

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

trait TracingFixtures extends Matchers { this: Suite =>

  def withOpenTracingMetricsProcessor[MyPayload, R](tracer: Tracer)(
    testWith: TestWith[OpenTracingMonitoringProcessor[MyPayload], R]): R = {
    val processor =
      new OpenTracingMonitoringProcessor[MyPayload]("namespace")(
        tracer,
        ExecutionContext.Implicits.global)
    testWith(processor)
  }

  def spanShouldBeTaggeddWith(span: Span,
                              exception: Throwable,
                              failureType: String) = {
    span
      .asInstanceOf[MockSpan]
      .tags()
      .asScala
      .toMap should contain only (("error" -> true), "error.type" -> failureType)
    val logEntries = span
      .asInstanceOf[MockSpan]
      .logEntries()
      .asScala
      .map(_.fields().asScala.toMap)
    logEntries should contain only (Map(
      ("event" -> "error"),
      ("error.object" -> exception)))
  }

}
