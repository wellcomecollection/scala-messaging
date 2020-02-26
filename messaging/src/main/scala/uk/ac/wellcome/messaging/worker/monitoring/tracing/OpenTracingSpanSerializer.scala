package uk.ac.wellcome.messaging.worker.monitoring.tracing

import java.util

import io.opentracing.propagation.{Format, TextMapAdapter}
import io.opentracing.{SpanContext, Tracer}

import scala.collection.JavaConverters._

/**
 * This trait allows to send and receive information
 * about a Span in messages so that we can chain spans
 * together in the same trace
 */
trait OpenTracingSpanSerializer[T] {
  def serialise(tracer: Tracer, span: SpanContext): T

  def deserialise(tracer: Tracer, t: T): SpanContext
}

object MapOpenTracingSpanSerializer$$ extends OpenTracingSpanSerializer[Map[String, String]] {

  override def serialise(tracer: Tracer,
                         span: SpanContext): Map[String, String] = {
    val map = new util.HashMap[String, String]()
    tracer.inject(span, Format.Builtin.TEXT_MAP, new TextMapAdapter(map))
    map.asScala.toMap
  }

  override def deserialise(tracer: Tracer, t: Map[String, String]): SpanContext =
    tracer.extract(Format.Builtin.TEXT_MAP, new TextMapAdapter(t.asJava))
}
