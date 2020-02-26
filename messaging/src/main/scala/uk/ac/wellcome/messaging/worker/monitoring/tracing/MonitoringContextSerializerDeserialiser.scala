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
trait MonitoringContextSerializerDeserialiser[InfraServiceMonitoringContext, T] {
  def serialise(monitoringContext: InfraServiceMonitoringContext): T

  def deserialise(t: T): InfraServiceMonitoringContext
}

trait MapOpenTracingSerializerDeserialiser
    extends MonitoringContextSerializerDeserialiser[
      SpanContext,
      Map[String, String]] {
  val tracer: Tracer
  override def serialise(
    monitoringContext: SpanContext): Map[String, String] = {
    val map = new util.HashMap[String, String]()
    tracer.inject(
      monitoringContext,
      Format.Builtin.TEXT_MAP,
      new TextMapAdapter(map))
    map.asScala.toMap
  }

  override def deserialise(t: Map[String, String]): SpanContext =
    tracer.extract(Format.Builtin.TEXT_MAP, new TextMapAdapter(t.asJava))
}
