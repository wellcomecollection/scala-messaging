package uk.ac.wellcome.messaging.fixtures.monitoring.tracing

import java.util

import io.opentracing.mock.MockSpan
import io.opentracing.propagation.{Format, TextMapAdapter}
import io.opentracing.{Span, SpanContext, Tracer}
import org.scalatest.{Matchers, Suite}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.worker.monitoring.tracing.{ContextCarrier, OpenTracingMonitoringProcessor}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

trait TracingFixtures extends Matchers{ this: Suite =>

  val textMapCarrier = new ContextCarrier[Map[String,String]] {
    override def inject(tracer: Tracer, span: SpanContext): Map[String, String] = {
      val map = new util.HashMap[String, String]()
      tracer.inject(span, Format.Builtin.TEXT_MAP, new TextMapAdapter(map))
      map.asScala.toMap
    }

    override def extract(tracer: Tracer, t: Map[String, String]): SpanContext = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapAdapter(t.asJava))
  }

  def withOpenTracingMetricsProcessor[MyWork,R](textMapCarrier: ContextCarrier[Map[String, String]], tracer: Tracer)(testWith:TestWith[OpenTracingMonitoringProcessor[MyWork],R]): R ={
    val processor = new OpenTracingMonitoringProcessor[MyWork]("namespace")(tracer, ExecutionContext.Implicits.global, textMapCarrier)
    testWith(processor)
  }

  def spanShouldBeTaggeddWith(span: Span, exception:Throwable, failureType: String) = {
    span.asInstanceOf[MockSpan].tags().asScala.toMap should contain only(("error" -> true), "error.type" -> failureType)
    val logEntries = span.asInstanceOf[MockSpan].logEntries().asScala.map(_.fields().asScala.toMap)
    logEntries should contain only (Map(("event" -> "error"), ("error.object" -> exception)))
  }

}
