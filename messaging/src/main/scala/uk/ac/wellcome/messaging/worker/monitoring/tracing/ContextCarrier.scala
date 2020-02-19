package uk.ac.wellcome.messaging.worker.monitoring.tracing

import java.util

import io.opentracing.propagation.{Format, TextMapAdapter}
import io.opentracing.{SpanContext, Tracer}

import scala.collection.JavaConverters._

trait ContextCarrier[T]{
  def inject(tracer: Tracer, span: SpanContext): T

  def extract(tracer: Tracer, t:T): SpanContext
}

object MapContextCarrier extends ContextCarrier[Map[String,String]]{

  override def inject(tracer: Tracer, span: SpanContext): Map[String, String] = {
    val map = new util.HashMap[String, String]()
    tracer.inject(span, Format.Builtin.TEXT_MAP, new TextMapAdapter(map))
    map.asScala.toMap
  }

  override def extract(tracer: Tracer, t: Map[String, String]): SpanContext = tracer.extract(Format.Builtin.TEXT_MAP, new TextMapAdapter(t.asJava))
}
